package analytics.pdf;

import analytics.model.StatsData;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class PdfReportGenerator {

  private static final byte[] XSL_FO_TEMPLATE;

  static {
    try (var in = PdfReportGenerator.class.getResourceAsStream("/xsl-fo/report.xsl")) {
      if (in == null) throw new RuntimeException("report.xsl not found on classpath");
      XSL_FO_TEMPLATE = in.readAllBytes();
    } catch (IOException e) { throw new RuntimeException("load report.xsl failed", e); }
  }

  private PdfReportGenerator() {}

  public static byte[] generate(StatsData data) throws Exception {
    String xml = data.toXml();
    var xslSource = new StreamSource(new ByteArrayInputStream(XSL_FO_TEMPLATE));
    var xmlSource = new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    var fopFactory = org.apache.fop.apps.FopFactory.newInstance(new File(".").toURI());
    var out = new ByteArrayOutputStream();
    var fop = fopFactory.newFop(org.apache.xmlgraphics.util.MimeConstants.MIME_PDF,
        fopFactory.newFOUserAgent(), out);

    var transformer = TransformerFactory.newInstance().newTransformer(xslSource);
    transformer.transform(xmlSource, new javax.xml.transform.sax.SAXResult(fop.getDefaultHandler()));
    return out.toByteArray();
  }

  public static void saveToFile(StatsData data, File file) throws Exception {
    Files.write(file.toPath(), generate(data));
  }
}
