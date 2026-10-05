"""Windows/local batch: 2 rounds x 12 fresh seeds x 4h; Luby HC + fixed DO.

Each JVM owns a private instance copy. Only the parent publishes batch metadata.
Manual stop discards active solution XMLs; completed runs and audit logs survive.
"""
import os
for key in ['OMP_NUM_THREADS','MKL_NUM_THREADS','OPENBLAS_NUM_THREADS']:
    os.environ[key]='1'
import argparse
import concurrent.futures as cf
import csv
import datetime as dt
import json
from pathlib import Path
import secrets
import shutil
import signal
import subprocess
import sys
import threading
import time
import traceback

from collect import HERE, SRC, INSTANCE, RESULTS, make_schema, write_json, sha, retry_file_operation

ACTIVE=HERE/'active_hybrid.json'
LOCK=HERE/'.collector.lock'  # Shared with original collector: never compile/run both batches at once.


def process_alive(pid):
    """Unknown/access-denied means alive; never reclaim an uncertain owner."""
    if pid<=0:return True
    if os.name=='nt':
        import ctypes
        from ctypes import wintypes
        kernel=ctypes.WinDLL('kernel32',use_last_error=True)
        kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
        kernel.OpenProcess.restype=wintypes.HANDLE
        kernel.GetExitCodeProcess.argtypes=[wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD)]
        kernel.CloseHandle.argtypes=[wintypes.HANDLE]
        handle=kernel.OpenProcess(0x1000,False,pid)
        if not handle:return ctypes.get_last_error()!=87  # invalid PID -> absent
        try:
            code=wintypes.DWORD()
            if not kernel.GetExitCodeProcess(handle,ctypes.byref(code)):return True
            return code.value==259
        finally:kernel.CloseHandle(handle)
    try:os.kill(pid,0)
    except ProcessLookupError:return False
    except PermissionError:return True
    return True


def acquire_lock(path=LOCK,active_paths=None):
    active_paths=active_paths or [ACTIVE,HERE/'active_run.json']
    for attempt in range(3):
        try:
            fd=os.open(path,os.O_CREAT|os.O_EXCL|os.O_WRONLY)
        except FileExistsError:
            snapshot=path.read_bytes()
            try:owner=json.loads(snapshot.decode('utf-8'))
            except (ValueError,UnicodeError):owner={}
            pid=owner.get('pid')
            # Protect the short window between exclusive create and publishing owner.
            if time.time()-path.stat().st_mtime<60:
                raise SystemExit(f'Lock recently created: {path}. Wait a minute and retry; another batch may be starting.')
            candidates=[owner] if pid is not None else []
            for active in active_paths:
                if active.exists():
                    try:candidates.append(json.loads(active.read_text(encoding='utf-8')))
                    except (ValueError,OSError):
                        raise SystemExit(f'Cannot verify lock owner: {active}. Check running tasks before removing {path}.')
            if not candidates:
                raise SystemExit(f'Old lock has no PID record: {path}. If no collect.py or hybrid_collect.py task is running, delete this file and retry. No solution files need deletion.')
            for candidate in candidates:
                try:alive=process_alive(int(candidate['pid']))
                except (KeyError,TypeError,ValueError):alive=True
                if alive:
                    raise SystemExit(f"Batch lock is occupied (PID {candidate.get('pid','unknown')}): {path}. Stop that batch first; the lock was not deleted.")
            # Recheck contents to avoid reclaiming a replacement lock.
            if path.read_bytes()!=snapshot:continue
            retry_file_operation(lambda:path.unlink())
            print(f'Removed stale batch lock: {path}',flush=True)
            continue
        with os.fdopen(fd,'w',encoding='utf-8') as stream:
            json.dump(dict(pid=os.getpid(),created=time.time()),stream)
        return
    raise SystemExit('Lock changed during startup; retry after other batch exits.')


def luby(index):
    if index<1:raise ValueError('Luby index is one-based')
    while True:
        power=1<<index.bit_length()
        if index==power-1:return power>>1
        index=index-(power>>1)+1


class Journal:
    def __init__(self,path):
        self.stream=path.open('w',newline='',encoding='utf-8');self.writer=None
    def add(self,row):
        if self.writer is None:
            self.writer=csv.DictWriter(self.stream,fieldnames=list(row));self.writer.writeheader()
        self.writer.writerow(row);self.stream.flush()
    def close(self):self.stream.close()


class Controller:
    def __init__(self,out):
        self.out=out;self.event=threading.Event();self.lock=threading.Lock();self.processes=set()
    def stopped(self):
        if (self.out/'STOP').exists():self.event.set()
        return self.event.is_set()
    def terminate(self):
        self.event.set()
        with self.lock:
            for p in self.processes:
                if p.poll() is None:
                    try:p.kill()
                    except ProcessLookupError:pass


class Bridge:
    def __init__(self,stage,controller,heap):
        self.controller=controller;self.stderr=(stage/'java_stderr.log').open('w',encoding='utf-8')
        opts={'creationflags':subprocess.CREATE_NEW_PROCESS_GROUP} if os.name=='nt' else {'start_new_session':True}
        self.p=subprocess.Popen(['java',f'-Xmx{heap}m','-XX:ActiveProcessorCount=1','-Dfile.encoding=UTF-8',
            '-cp',str(HERE/'build'),'solver.DO.SearchBridge',str(stage/'instance.xml'),
            str(stage/'hc_improvements.csv'),'improvements'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,
            stderr=self.stderr,text=True,encoding='utf-8',**opts)
        with controller.lock:controller.processes.add(self.p)
        if self.p.stdout.readline().strip()!='READY\t989':
            self.close()
            raise RuntimeError('Java initialization failed')
    def send(self,*fields):
        if self.controller.stopped():raise InterruptedError('manual stop')
        self.p.stdin.write('\t'.join(map(str,fields))+'\n');self.p.stdin.flush()
        response=self.p.stdout.readline().strip()
        if not response:raise RuntimeError('Java exited; see java_stderr.log')
        return response
    def close(self):
        if self.p.poll() is None:
            try:
                self.p.stdin.write('QUIT\n');self.p.stdin.flush()
                self.p.wait(timeout=5)
            except (OSError,subprocess.TimeoutExpired):self.p.kill()
        self.p.wait(timeout=10)
        self.p.stdin.close();self.p.stdout.close();self.stderr.close()
        with self.controller.lock:self.controller.processes.discard(self.p)


def parse_state(response):
    df,nfe,choices=response.split('\t')
    return int(df),int(nfe),np.array(list(map(int,choices.split(','))),dtype=np.int32)


def propose(model,choices,fullidx,blocks,scale,rng):
    current=choices[fullidx]
    x=np.zeros((1,sum(b['size'] for b in blocks)),np.float32)
    for j,b in enumerate(blocks):x[0,b['offset']+current[j]]=1
    x=torch.from_numpy(x)
    with torch.no_grad():
        h=model.encoder(x);base,_,_=model.decode(x,h)
        noise=(rng.normal(0,.75,512)*scale).astype(np.float32)
        changed,_,_=model.decode(x,h+torch.from_numpy(noise[None,:]))
    ranked=[]
    for j,(a,b) in enumerate(zip(base,changed)):
        delta=(b-a)[0].numpy().copy();delta[current[j]]=-np.inf
        category=int(delta.argmax());gain=float(delta[category])
        if gain>1e-7:ranked.append((gain,j,category))
    ranked.sort(reverse=True)
    return ','.join(f'{fullidx[j]}:{v}' for gain,j,v in ranked[:2]),float(np.linalg.norm(noise))


def run_worker(spec,out,controller,args,model,blocks,fullidx,scale,instance_bytes):
    name=f"round_{spec['round']:02}_seed_{spec['seed']}"
    stage=out/'.pending'/name;stage.mkdir()
    (stage/'instance.xml').write_bytes(instance_bytes)
    write_json(stage/'config.json',dict(**spec,hc_base=10000,do_proposals=args.do_budget,luby_index_start=1,
        seconds=args.seconds,do_budget=args.do_budget,restart=False))
    bridge=None;phases=Journal(stage/'phases.csv');dolog=Journal(stage/'do_proposals.csv')
    checks=Journal(stage/'checkpoints.csv');status='failed';error=None;result={}
    try:
        bridge=Bridge(stage,controller,args.heap_mb)
        initial_df,nfe,choices=parse_state(bridge.send('RANDOM',spec['seed']))
        initial=choices.copy();df=initial_df
        bridge.send('SAVE',stage/'initial.xml')
        started=time.monotonic();deadline=int(bridge.send('STATS').split('\t')[2])+int(args.seconds*1e9)
        next_save=600;cycle=do_total=do_accept=do_improve=hc_total=0
        rng=np.random.default_rng(spec['noise_seed'])
        while time.monotonic()-started<args.seconds and df>0 and not controller.stopped():
            cycle+=1;multiplier=luby(cycle);target=10000*multiplier
            before,start_nfe=df,nfe;tic=time.monotonic();remaining=target
            while remaining>0 and df>0 and time.monotonic()-started<args.seconds and not controller.stopped():
                old_nfe=nfe;chunk=min(remaining,1000)
                df,nfe,choices=parse_state(bridge.send('HC_TIME',chunk,cycle,deadline))
                actual=nfe-old_nfe;remaining-=actual
                if actual<chunk:break
            hc_total+=nfe-start_nfe
            phases.add(dict(cycle=cycle,phase='hc',multiplier=multiplier,hc_target=target,before_df=before,
                after_df=df,candidate_nfe=nfe-start_nfe,seconds=time.monotonic()-tic,
                elapsed_seconds=time.monotonic()-started,nfe=nfe))
            if controller.stopped():raise InterruptedError('manual stop')
            if time.monotonic()-started>=args.seconds or df==0:break
            before,start_nfe=df,nfe;tic=time.monotonic()
            for step in range(1,args.do_budget+1):
                if time.monotonic()-started>=args.seconds or df==0:break
                patch,noise_l2=propose(model,choices,fullidx,blocks,scale,rng)
                fields=bridge.send('PATCH',cycle,step,patch).split('\t')
                b,candidate=int(fields[0]),int(fields[1]);accepted=fields[2]=='true';changed=int(fields[3])
                df,nfe,choices=parse_state('\t'.join(fields[4:]))
                assert accepted==(candidate<=b) and df==min(b,candidate)
                do_total+=1;do_accept+=int(accepted and bool(patch));do_improve+=int(candidate<b)
                dolog.add(dict(cycle=cycle,step=step,before_df=b,candidate_df=candidate,after_df=df,
                    accepted=accepted,effective_accepted=accepted and bool(patch),changed_classes=changed,
                    changed_blocks=len(patch.split(',')) if patch else 0,patch=patch,noise_l2=noise_l2,
                    nfe=nfe,elapsed_seconds=time.monotonic()-started))
            phases.add(dict(cycle=cycle,phase='do',multiplier=multiplier,hc_target=target,before_df=before,
                after_df=df,candidate_nfe=nfe-start_nfe,seconds=time.monotonic()-tic,
                elapsed_seconds=time.monotonic()-started,nfe=nfe))
            if time.monotonic()-started>=next_save:
                bridge.send('SAVE',stage/'current.xml')
                checks.add(dict(seconds=time.monotonic()-started,df=df,nfe=nfe,cycle=cycle,
                    distance_blocks=int((choices!=initial).sum()),do_candidates=do_total,do_effective=do_accept))
                write_json(stage/'progress.json',dict(df=df,nfe=nfe,cycle=cycle,elapsed_seconds=time.monotonic()-started))
                next_save+=600
        if controller.stopped():raise InterruptedError('manual stop')
        bridge.send('SAVE',stage/'final.xml');elapsed=time.monotonic()-started
        assert int(bridge.send('VERIFY',stage/'final.xml'))==df
        assert nfe==1+hc_total+do_total
        status='completed'
        result=dict(**spec,status=status,initial_df=initial_df,final_df=df,nfe=nfe,hc_nfe=hc_total,
            do_nfe=do_total,do_effective=do_accept,do_improvements=do_improve,cycles=cycle,
            elapsed_seconds=elapsed,final_distance_blocks=int((choices!=initial).sum()),verified=True,
            timing='search includes inference/HC/DF/logging/checkpoint saves; excludes JVM startup and initial save')
    except Exception:
        status='discarded' if controller.stopped() else 'failed';error=traceback.format_exc()
        result=dict(**spec,status=status,error=error)
    finally:
        if bridge is not None:bridge.close()
        phases.close();dolog.close();checks.close()
    if status=='completed':
        phase_rows=list(csv.DictReader((stage/'phases.csv').open(encoding='utf-8')))
        result['hc_seconds']=sum(float(r['seconds']) for r in phase_rows if r['phase']=='hc')
        result['do_seconds']=sum(float(r['seconds']) for r in phase_rows if r['phase']=='do')
        first={}
        with (stage/'hc_improvements.csv').open(encoding='utf-8') as stream:
            for row in csv.DictReader(stream):
                for target_df in [10,5,3,2,1,0]:
                    if int(row['after_df'])<=target_df and str(target_df) not in first:
                        first[str(target_df)]=dict(nfe=int(row['nfe']),seconds=int(row['elapsed_ns'])/1e9,phase=row['phase'])
        result['first_hits']=first
        result['first_hit_clock']='Java clock from RANDOM, includes initial random generation/save'
        # Completion publication and stop share a lock: no pending solution is promoted after stop.
        with controller.lock:
            if controller.stopped():status='discarded';result.update(status=status,verified=False)
            else:
                write_json(stage/'summary.json',result)
                target=out/'completed'/name
                retry_file_operation(lambda:os.replace(stage,target))
    if status!='completed':
        # Keep audit logs, discard all partial solutions including initial/current/final XML.
        for xml in stage.glob('*.xml'):retry_file_operation(lambda p=xml:p.unlink(missing_ok=True))
        write_json(stage/'summary.json',result)
        target=out/('discarded' if status=='discarded' else 'errors')/name
        retry_file_operation(lambda:os.replace(stage,target))
    return result


def stop_request():
    if not ACTIVE.exists():raise SystemExit('No active hybrid batch')
    active=json.loads(ACTIVE.read_text(encoding='utf-8'));Path(active['output'],'STOP').touch()
    print('Stop requested: active solutions will be discarded; completed runs retained.')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workers',type=int,default=12)
    parser.add_argument('--rounds',type=int,default=2)
    parser.add_argument('--seconds',type=float,default=14400)
    parser.add_argument('--do-budget',type=int,default=100)
    parser.add_argument('--heap-mb',type=int,default=512)
    parser.add_argument('--output',type=Path)
    parser.add_argument('--stop',action='store_true')
    args=parser.parse_args()
    if args.stop:return stop_request()
    if min(args.workers,args.rounds,args.seconds,args.heap_mb,args.do_budget)<=0:parser.error('Positive budgets required')
    global np,torch
    import numpy as np
    import torch
    from torch import nn
    torch.set_num_threads(1);torch.set_num_interop_threads(1)
    class GatedAE(nn.Module):
        def __init__(self,blocks):
            super().__init__();self.sizes=[b['size'] for b in blocks]
            self.encoder=nn.Sequential(nn.Linear(sum(self.sizes),512),nn.ReLU(),nn.Linear(512,512),nn.LayerNorm(512))
            self.replacement=nn.Linear(512,sum(self.sizes));self.gate=nn.Linear(512,len(blocks))
        def decode(self,x,h):
            logits=self.replacement(h);gl=self.gate(h);gates=gl.sigmoid()
            probs=[(1-gates[:,j:j+1])*xb+gates[:,j:j+1]*lb.softmax(1)
                   for j,(xb,lb) in enumerate(zip(x.split(self.sizes,1),logits.split(self.sizes,1)))]
            return probs,gates,gl
    for binary in ['java','javac']:
        if shutil.which(binary) is None:parser.error('JDK17+ java and javac must be on PATH')
    modeldir=HERE/'hybrid_model'
    schema=make_schema(INSTANCE);blocks=json.loads((modeldir/'reduced_encoding_schema.json').read_text(encoding='utf-8'))['blocks']
    fullidx=[i for i,b in enumerate(schema['blocks']) if b['size']>1]
    expected=[{k:b[k] for k in ['class_id','kind','size','options']} for b in blocks]
    actual=[{k:schema['blocks'][i][k] for k in ['class_id','kind','size','options']} for i in fullidx]
    if actual!=expected:parser.error('Instance categories do not match model schema')
    model=GatedAE(blocks);model.load_state_dict(torch.load(modeldir/'model.pt',map_location='cpu',weights_only=True));model.eval()
    scale=np.load(modeldir/'latent_std.npy');assert scale.shape==(512,)
    out=(args.output or RESULTS/dt.datetime.now().strftime('hc_do_luby_%Y%m%d_%H%M%S')).resolve()
    acquire_lock()
    controller=None
    try:
        out.mkdir(parents=True,exist_ok=False)
        for name in ['completed','.pending','discarded','errors','sources']:(out/name).mkdir()
        controller=Controller(out)
        write_json(ACTIVE,dict(output=str(out),pid=os.getpid()))
        def handler(*unused):controller.event.set()
        for name in ['SIGINT','SIGTERM','SIGBREAK']:
            if hasattr(signal,name):signal.signal(getattr(signal,name),handler)
        seeds=[];seen=set()
        for round_id in range(1,args.rounds+1):
            for slot in range(args.workers):
                pair=[]
                for _ in range(2):
                    while True:
                        seed=secrets.randbelow(2**63-1)
                        if seed not in seen:break
                    seen.add(seed);pair.append(seed)
                seeds.append(dict(round=round_id,slot=slot,seed=pair[0],noise_seed=pair[1]))
        write_json(out/'seed_manifest.json',seeds)
        shutil.copy2(INSTANCE,out/'instance.xml');write_json(out/'encoding_schema.json',schema)
        for filename in ['hybrid_collect.py','run_hybrid_windows.bat','stop_hybrid_windows.bat','SearchBridge.java','EvaluateSolutions.java','collect.py','README_hybrid.md','requirements_hybrid.txt']:
            shutil.copy2(HERE/filename,out/'sources'/filename)
        for directory in ['dataset','io','utils']:
            for source in (SRC/directory).rglob('*.java'):
                target=out/'sources/core'/source.relative_to(SRC)
                target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        shutil.copytree(modeldir,out/'model_snapshot')
        config=dict(workers=args.workers,rounds=args.rounds,seconds_per_task=args.seconds,
            nominal_search_hours=args.rounds*args.seconds/3600,hc_base=10000,do_budget=args.do_budget,
            sigma=.75,coordinates=512,block_budget=2,luby_index_start=1,restart=False,
            manual_stop='discard active solution XMLs, preserve completed solutions and interrupted logs',
            instance_sha256=sha(INSTANCE),model_sha256=sha(modeldir/'model.pt'),
            scale_sha256=sha(modeldir/'latent_std.npy'),torch_version=torch.__version__,python_version=sys.version,
            source_sha256={p.relative_to(out/'sources').as_posix():sha(p) for p in (out/'sources').rglob('*') if p.is_file()},
            timer='each task has own4h search clock; round barrier; startup and verification add overhead',
            hc_log='strict improvements plus per-cycle actualNFE; DO log every candidate')
        write_json(out/'config.json',config)
        (HERE/'build').mkdir(exist_ok=True)
        compiled=subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',str(HERE/'build'),
            '-sourcepath',str(SRC),str(HERE/'SearchBridge.java'),str(HERE/'EvaluateSolutions.java')],capture_output=True,text=True)
        (out/'compile.log').write_text(compiled.stdout+compiled.stderr,encoding='utf-8');compiled.check_returncode()
        results=[];instance_bytes=(out/'instance.xml').read_bytes()
        print(f'OUTPUT: {out}\n{args.rounds} rounds x {args.workers} unique seeds; {args.seconds}s/task',flush=True)
        for round_id in range(1,args.rounds+1):
            if controller.stopped():break
            print(f'Start round {round_id}',flush=True)
            with cf.ThreadPoolExecutor(max_workers=args.workers) as pool:
                pending={pool.submit(run_worker,s,out,controller,args,model,blocks,fullidx,scale,instance_bytes)
                         for s in seeds if s['round']==round_id}
                last=time.monotonic()
                while pending:
                    if controller.stopped():controller.terminate()
                    done,pending=cf.wait(pending,timeout=.5,return_when=cf.FIRST_COMPLETED)
                    for future in done:
                        result=future.result();results.append(result)
                        print(f"round{result['round']} seed{result['seed']}: {result['status']} DF={result.get('final_df')}",flush=True)
                        write_json(out/'results.json',results)
                    if time.monotonic()-last>=60:
                        print(f'round{round_id}: {len(pending)} tasks running',flush=True);last=time.monotonic()
            if any(r['status']=='failed' for r in results):
                print('Worker errors detected; next round will not start. See errors/.',flush=True);break
        write_json(out/'run_status.json',dict(status='stopped' if controller.stopped() else 'failed' if any(r['status']=='failed' for r in results) else 'completed',
            completed=sum(r['status']=='completed' for r in results),discarded=sum(r['status']=='discarded' for r in results),
            failed=sum(r['status']=='failed' for r in results),planned=args.workers*args.rounds))
        if any(r['status']=='failed' for r in results):raise SystemExit(1)
    finally:
        if controller is not None and controller.stopped():controller.terminate()
        retry_file_operation(lambda:LOCK.unlink(missing_ok=True))
        retry_file_operation(lambda:ACTIVE.unlink(missing_ok=True))


if __name__=='__main__':main()
