package io;

import java.io.File;
import java.io.Writer;
import java.time.Instant;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import dataset.Class;
import dataset.Event;
import dataset.Timetable;
import solver.Solution;

public class Decoder {

    public Decoder(Solution solution) {
        try {
            File outputDir = new File("./solution");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            Document document = createXml(solution);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            String fileName = generateFileName(solution);
            File outputFile = new File(outputDir, fileName);
            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(outputFile)
            );
        } catch (ParserConfigurationException | TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    public Decoder(Solution solution, Writer dest) {
        try {
            File outputDir = new File("./solution");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            Document document = createXml(solution);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(dest)
            );
        } catch (ParserConfigurationException | TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    private Document createXml(Solution solution) throws ParserConfigurationException {
        Timetable timetable = solution.timetable();
        Event[] events = timetable.getEvents();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        Element rootElement = document.createElement("solution");

        rootElement.setAttribute("name", solution.name());
        rootElement.setAttribute("runtime", String.valueOf(solution.runTime()));
        rootElement.setAttribute("cores", String.valueOf(solution.cores()));
        rootElement.setAttribute("technique", solution.technique());
        rootElement.setAttribute("author", solution.author());
        rootElement.setAttribute("institution", solution.institution());
        rootElement.setAttribute("country", solution.country());
        document.appendChild(rootElement);

        for (Event e : events) {
            if (e != null) {
                Element classElement = document.createElement("class");
                Class aClass = e.getTheClass();
                classElement.setAttribute("id", String.valueOf(aClass.id()));
                classElement.setAttribute("days", convertToString(e.getTimeAssignment().time().days()));
                classElement.setAttribute("start", String.valueOf(e.getTimeAssignment().time().start()));
                classElement.setAttribute("weeks", convertToString(e.getTimeAssignment().time().weeks()));
                if (e.getRoomAssignment() != null) {
					classElement.setAttribute("room", String.valueOf(e.getRoomAssignment().room().id()));
				}
                rootElement.appendChild(classElement);
            }
        }

        // The student should put here.

        return document;
    }

    private String convertToString(boolean[] array) {
        if (array == null) {
			throw new IllegalArgumentException("Array cannot be null");
		}

        StringBuilder builder = new StringBuilder(array.length);
        for (boolean value : array) {
            builder.append(value ? '1' : '0');  // true→'1', false→'0'
        }
        return builder.toString();
    }

    private String generateFileName(Solution solution) {
        long unixTimestamp = Instant.now().getEpochSecond();

        String safeInstance = solution.name()
                .replace(" ", "_")
                .replace(":", "-")
                .replace("/", "-");

        return String.format("solution_%s_%d.xml",
                safeInstance, unixTimestamp);
    }

}
