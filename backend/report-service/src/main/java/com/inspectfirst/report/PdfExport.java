package com.inspectfirst.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.io.*;

/** PDF is a representation of the immutable, asynchronously prepared completion report. */
public class PdfExport {
    public static byte[] render(JsonNode snapshot,int revision,String hash) throws IOException {
        try(var document=new PDDocument();var fontStream=PdfExport.class.getResourceAsStream("/fonts/NotoSansSC.ttf");var output=new ByteArrayOutputStream()) {
            if(fontStream==null) throw new IOException("Report font missing");
            var font=PDType0Font.load(document,fontStream);var writer=new Pages(document,font);
            var guide=snapshot.path("guideSnapshot");
            document.getDocumentInformation().setTitle("InspectFirst inspection report");
            writer.text("InspectFirst",23);writer.text("Inspection report - completion revision "+revision,16);
            writer.text(guide.path("product").path("name").asText(guide.path("category").path("name").asText("Inspection")),15);
            writer.text("Guide version: "+guide.path("version").asInt()+" | Inspection: "+snapshot.path("id").asText(),10);
            writer.text("Self-check observations only. Not an authenticity certificate or a quality guarantee. Unchecked and inconclusive checks are not passes.",11);
            writer.text("Purchase requirements",14);writer.text(snapshot.path("userRequirement").asText(),11);
            writer.text("Seller claims",14);writer.text(snapshot.path("sellerClaim").asText(),11);
            writer.text("Summary",14);
            for(String status:java.util.List.of("NO_ISSUE_OBSERVED","ISSUE_OBSERVED","UNCHECKED","UNABLE_TO_ASSESS")) writer.text(status+": "+snapshot.path("summary").path(status).asLong(),10);
            for(var item:snapshot.path("items")) {
                var content=item.path("contentSnapshot");
                writer.text(content.path("order").asInt()+". "+content.path("title").asText(),14);
                writer.text("Result: "+item.path("resultStatus").asText(),11);
                writer.text("Notes: "+item.path("note").asText(),11);
                writer.text("How to check",12);
                for(var step:content.path("steps")) writer.text("- "+step.asText(),10);
                writer.text("Expected observations: "+content.path("expectedObservation").asText(),10);
                writer.text("Possible problems: "+content.path("abnormalSigns").asText(),10);
                writer.text("Limits: "+content.path("limitations").asText(),10);
                for(var source:content.path("sources")) writer.text("Source: "+source.path("title").asText()+" | "+source.path("publisher").asText()+" | "+source.path("url").asText(),9);
                if(item.path("attachments").size()>0) writer.text("Photos attached at completion: "+item.path("attachments").size()+". Access originals through the authenticated inspection record.",9);
            }
            writer.text("Snapshot SHA-256: "+hash,8);writer.text("Integrity metadata is not a certification. Report text follows the saved completion revision.",9);
            writer.close();document.save(output);return output.toByteArray();
        }
    }
    private static class Pages implements AutoCloseable {
        final PDDocument doc;final PDFont font;PDPageContentStream stream;float y;int page;
        Pages(PDDocument doc,PDFont font) throws IOException { this.doc=doc;this.font=font;next(); }
        void next() throws IOException {
            if(stream!=null) finish();var sheet=new PDPage(PDRectangle.A4);doc.addPage(sheet);stream=new PDPageContentStream(doc,sheet);y=785;page++;
        }
        String supported(String text) {
            StringBuilder out=new StringBuilder();
            text.codePoints().forEach(cp->{
                if(cp=='\n') out.append('\n');
                else if(Character.isISOControl(cp)) out.append(' ');
                else {String value=new String(Character.toChars(cp));try {font.getStringWidth(value);out.append(value);}catch(Exception e){out.append("[U+").append(Integer.toHexString(cp).toUpperCase()).append(']');}}
            });return out.toString();
        }
        void text(String value,float size) throws IOException {
            String clean=supported(value);StringBuilder line=new StringBuilder();float width=0;
            for(int cp:clean.codePoints().toArray()) {
                String chr=new String(Character.toChars(cp));float advance=cp=='\n'?0:font.getStringWidth(chr)/1000*size;
                if(cp=='\n'||width+advance>490) {line(line.toString(),size);line.setLength(0);width=0;}
                if(cp!='\n'){line.append(chr);width+=advance;}
            }
            if(!line.isEmpty()) line(line.toString(),size);y-=7;
        }
        void line(String text,float size) throws IOException {
            if(y-size<50) next();stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(50,y);stream.showText(text);stream.endText();y-=size*1.5f;
        }
        void finish() throws IOException {
            stream.beginText();stream.setFont(font,8);stream.newLineAtOffset(50,25);stream.showText("InspectFirst - observations, not certification | Page "+page);stream.endText();stream.close();
        }
        @Override public void close() throws IOException { if(stream!=null) {finish();stream=null;} }
    }
}
