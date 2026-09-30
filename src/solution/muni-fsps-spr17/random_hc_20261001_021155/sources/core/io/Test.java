package io;

import dataset.ProblemInstance;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
//        src/encoder/check/xml/agh-fal17.xml   No Running Errors   check
//        src/encoder/check/xml/agh-fis-spr17.xml   No Running Errors   check
//        src/encoder/check/xml/agh-ggos-spr17.xml  No Running Errors   check
//        src/encoder/check/xml/agh-h-spr17.xml No Running Errors   check
//        src/encoder/check/xml/bet-fal17.xml   No Running Errors   check
//        src/encoder/check/xml/bet-spr18.xml   No Running Errors   check
//        src/encoder/check/xml/bet-sum18.xml   No Running Errors   check
//        src/encoder/check/xml/iku-fal17.xml   No Running Errors   check
//        src/encoder/check/xml/iku-spr18.xml   No Running Errors   check
//        src/encoder/check/xml/lums-fal17.xml  No Running Errors   check
//        src/encoder/check/xml/lums-spr18.xml  No Running Errors   check
//        src/encoder/check/xml/lums-sum17.xml  No Running Errors   check
//        src/encoder/check/xml/mary-fal18.xml  No Running Errors   check
//        src/encoder/check/xml/mary-spr17.xml  No Running Errors   check
//        src/encoder/check/xml/muni-fi-fal17.xml   No Running Errors   check
//        src/encoder/check/xml/muni-fi-spr16.xml   No Running Errors   check
//        src/encoder/check/xml/muni-fi-spr17.xml   No Running Errors   check
//        src/encoder/check/xml/muni-fsps-spr17.xml No Running Errors   check
//        src/encoder/check/xml/muni-fsps-spr17c.xml    No Running Errors   check
//        src/encoder/check/xml/muni-fspsx-fal17.xml    No Running Errors   check
//        src/encoder/check/xml/muni-pdf-spr16.xml  No Running Errors   check
//        src/encoder/check/xml/muni-pdf-spr16c.xml No Running Errors   check
//        src/encoder/check/xml/muni-pdfx-fal17.xml No Running Errors   check
//        src/encoder/check/xml/nbi-spr18.xml   No Running Errors   check
//        src/encoder/check/xml/pu-c8-spr07.xml No Running Errors   courseSkip"591"
//        src/encoder/check/xml/pu-cs-fal07.xml No Running Errors   check
//        src/encoder/check/xml/pu-d5-spr17.xml No Running Errors   check
//        src/encoder/check/xml/pu-d9-fal19.xml No Running Errors   check
//        src/encoder/check/xml/pu-llr-spr07.xml    No Running Errors   courseSkip"363"
//        src/encoder/check/xml/pu-llr-spr17.xml    No Running Errors   check
//        src/encoder/check/xml/pu-proj-fal19.xml   No Running Errors   check
//        src/encoder/check/xml/tg-fal17.xml    No Running Errors   check
//        src/encoder/check/xml/tg-spr18.xml    No Running Errors   check
//        src/encoder/check/xml/wbg-fal10.xml   No Running Errors   check
//        src/encoder/check/xml/yach-fal17.xml  No Running Errors   check
public class Test {
    public static void main (String[] args) throws ParserConfigurationException, IOException, SAXException {
        // 创建日志目录
        String logDir = "log";
        File logDirFile = new File(logDir);
        if (!logDirFile.exists()) {
            logDirFile.mkdirs();
        }
        
        // 创建日志文件
        // String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String logFileName = logDir + "/encoder_log_" + ".log";

        // 创建输出流并重定向
        PrintStream logFile = new PrintStream(new FileOutputStream(logFileName));
        
        // 保存原始的System.out
        PrintStream originalOut = System.out;
        
        // 重定向System.out到日志文件
        System.setOut(logFile);
        
        try {
            Encoder encoder = new Encoder("/Users/mingxuan/OR/itc/xml/agh-fal17.xml");
            ProblemInstance problemInstance = encoder.getProblemInstance();
            problemInstance.printStats();
//            System.out.println(encoder);
//            encoder.printRooms();
//            encoder.printTravelMatrix();
//            encoder.printCourses();
//            encoder.printStudents();
//            encoder.printDistribution();
        } catch (Exception e) {
            System.out.println("Error: ");
            e.printStackTrace(System.out);
        } finally {
            // 关闭日志文件
            logFile.close();
            // 恢复原始输出
            System.setOut(originalOut);
            System.out.println("Save log to: " + logFileName);
        }
    }
}
