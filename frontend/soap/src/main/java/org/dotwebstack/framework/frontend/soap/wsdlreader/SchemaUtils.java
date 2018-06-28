package org.dotwebstack.framework.frontend.soap.wsdlreader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;

import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * This class was extracted from the soapUI code base by centeractive ag in October 2011.
 * The main reason behind the extraction was to separate the code that is responsible
 * for the generation of the SOAP messages from the rest of the soapUI's code that is
 * tightly coupled with other modules, such as soapUI's graphical user interface, etc.
 * The goal was to create an open-source java project whose main responsibility is to
 * handle SOAP message generation and SOAP transmission purely on an XML level.
 * <br/>
 * centeractive ag would like to express strong appreciation to SmartBear Software and
 * to the whole team of soapUI's developers for creating soapUI and for releasing its
 * source code under a free and open-source licence. centeractive ag extracted and
 * modifies some parts of the soapUI's code in good faith, making every effort not
 * to impair any existing functionality and to supplement it according to our
 * requirements, applying best practices of software design.
 *
 * <p>Changes done:
 * - changing location in the package structure
 * - removal of dependencies and code parts that are out of scope of SOAP message generation
 * - minor fixes to make the class compile out of soapUI's code base
 * - changing the mechanism using which external resources are loaded
 */

/**
 * XML-Schema related tools
 *
 * @author Ole.Matzura
 */
class SchemaUtils {
  private static final Logger LOG = LoggerFactory.getLogger(SchemaUtils.class);

  private static Map<String, XmlObject> defaultSchemas = new HashMap<String, XmlObject>();

  public static final boolean STRICT_SCHEMA_TYPES = false;

  static {
    initDefaultSchemas();
  }

  public static void initDefaultSchemas() {
    try {
      defaultSchemas.clear();
    } catch (Exception e) {
      throw new SoapBuilderException(e);
    }
  }

  public static SchemaTypeSystem loadSchemaTypes(String wsdlUrl, SchemaLoader loader) {
    try {
      ArrayList<XmlObject> schemas = new ArrayList<XmlObject>(getSchemas(wsdlUrl, loader).values());
      return buildSchemaTypes(schemas);
    } catch (Exception e) {
      throw new SoapBuilderException(e);
    }
  }

  public static SchemaTypeSystem buildSchemaTypes(List<XmlObject> schemas) {
    XmlOptions options = new XmlOptions();
    options.setCompileNoValidation();
    options.setCompileNoPvrRule();
    options.setCompileDownloadUrls();
    options.setCompileNoUpaRule();
    options.setValidateTreatLaxAsSkip();

    for (int c = 0; c < schemas.size(); c++) {
      XmlObject xmlObject = schemas.get(c);
      if (xmlObject == null
          || !((Document) xmlObject.getDomNode()).getDocumentElement().getNamespaceURI()
          .equals(Constants.XSD_NS)) {
        schemas.remove(c);
        c--;
      }
    }

    // TODO //SoapUI.getSettings().getBoolean( WsdlSettings.STRICT_SCHEMA_TYPES );
    boolean strictSchemaTypes = STRICT_SCHEMA_TYPES;
    if (!strictSchemaTypes) {
      Set<String> mdefNamespaces = new HashSet<String>();

      for (XmlObject xmlObj : schemas) {
        mdefNamespaces.add(getTargetNamespace(xmlObj));
      }

      options.setCompileMdefNamespaces(mdefNamespaces);
    }

    ArrayList<?> errorList = new ArrayList<Object>();
    options.setErrorListener(errorList);

    XmlCursor cursor = null;

    try {
      // remove imports
      for (int c = 0; c < schemas.size(); c++) {
        XmlObject s = schemas.get(c);

        Map<?, ?> map = new HashMap<String, String>();
        cursor = s.newCursor();
        cursor.toStartDoc();
        if (toNextContainer(cursor)) {
          cursor.getAllNamespaces(map);
        } else {
          LOG.warn("Can not get namespaces for " + s);
        }

        String tns = getTargetNamespace(s);

        // log.info( "schema for [" + tns + "] contained [" + map.toString()
        // + "] namespaces" );

        if (strictSchemaTypes && defaultSchemas.containsKey(tns)) {
          schemas.remove(c);
          c--;
        } else {
          removeImports(s);
        }

        cursor.dispose();
        cursor = null;
      }

      // schemas.add( soapVersion.getSoapEncodingSchema());
      // schemas.add( soapVersion.getSoapEnvelopeSchema());
      schemas.addAll(defaultSchemas.values());

      SchemaTypeSystem sts = XmlBeans.compileXsd(schemas.toArray(new XmlObject[schemas.size()]),
          XmlBeans.getBuiltinTypeSystem(), options);

      return sts;
      // return XmlBeans.typeLoaderUnion(new SchemaTypeLoader[] { sts,
      // XmlBeans.getBuiltinTypeSystem() });
    } catch (Exception e) {
      throw new SoapBuilderException(e);
    } finally {
      for (int c = 0; c < errorList.size(); c++) {
        LOG.warn("Error: " + errorList.get(c));
      }

      if (cursor != null) {
        cursor.dispose();
      }
    }
  }

  public static boolean toNextContainer(XmlCursor cursor) {
    while (!cursor.isContainer() && !cursor.isEnddoc()) {
      cursor.toNextToken();
    }

    return cursor.isContainer();
  }

  public static String getTargetNamespace(XmlObject s) {
    return ((Document) s.getDomNode()).getDocumentElement().getAttribute("targetNamespace");
  }

  public static Map<String, XmlObject> getSchemas(String wsdlUrl, SchemaLoader loader) {
    Map<String, XmlObject> result = new HashMap<String, XmlObject>();
    getSchemas(wsdlUrl, result, loader, null /* , false */);
    return result;
  }

  /**
   * Returns a map mapping urls to corresponding XmlSchema XmlObjects for the
   * specified wsdlUrl
   */
  public static void getSchemas(
      String wsdlUrl,
      Map<String, XmlObject> existing,
      SchemaLoader loader,
      String tns) {
    if (existing.containsKey(wsdlUrl)) {
      return;
    }

    ArrayList<?> errorList = new ArrayList<Object>();

    Map<String, XmlObject> result = new HashMap<String, XmlObject>();

    boolean common = false;

    try {
      XmlOptions options = new XmlOptions();
      options.setCompileNoValidation();
      options.setSaveUseOpenFrag();
      options.setErrorListener(errorList);
      options.setSaveSyntheticDocumentElement(new QName(Constants.XSD_NS, "schema"));

      XmlObject xmlObject = loader.loadXmlObject(wsdlUrl, options);
      if (xmlObject == null) {
        throw new Exception("Failed to load schema from [" + wsdlUrl + "]");
      }

      Document dom = (Document) xmlObject.getDomNode();
      Node domNode = dom.getDocumentElement();

      // is this an xml schema?
      if (domNode.getLocalName().equals("schema")
          && Constants.XSD_NS.equals(domNode.getNamespaceURI())) {
        // set targetNamespace (this happens if we are following an include
        // statement)
        if (tns != null) {
          Element elm = ((Element) domNode);
          if (!elm.hasAttribute("targetNamespace")) {
            common = true;
            elm.setAttribute("targetNamespace", tns);
          }

          // check for namespace prefix for targetNamespace
          NamedNodeMap attributes = elm.getAttributes();
          int c = 0;
          for (; c < attributes.getLength(); c++) {
            Node item = attributes.item(c);
            if (item.getNodeName().equals("xmlns")) {
              break;
            }

            if (item.getNodeValue().equals(tns) && item.getNodeName().startsWith("xmlns")) {
              break;
            }
          }

          if (c == attributes.getLength()) {
            elm.setAttribute("xmlns", tns);
          }
        }

        if (common && !existing.containsKey(wsdlUrl + "@" + tns)) {
          result.put(wsdlUrl + "@" + tns, xmlObject);
        } else {
          result.put(wsdlUrl, xmlObject);
        }
      } else {
        existing.put(wsdlUrl, null);

        XmlObject[] schemas = xmlObject.selectPath("declare namespace s='"
            + Constants.XSD_NS + "' .//s:schema");

        for (int i = 0; i < schemas.length; i++) {
          XmlCursor xmlCursor = schemas[i].newCursor();
          String xmlText = xmlCursor.getObject().xmlText(options);
          // schemas[i] = XmlObject.Factory.parse( xmlText, options );
          schemas[i] = XmlUtils.createXmlObject(xmlText, options);
          schemas[i].documentProperties().setSourceName(wsdlUrl);

          result.put(wsdlUrl + "@" + (i + 1), schemas[i]);
        }

        XmlObject[] wsdlImports = xmlObject.selectPath("declare namespace s='"
            + Constants.WSDL11_NS + "' .//s:import/@location");
        for (int i = 0; i < wsdlImports.length; i++) {
          String location = ((SimpleValue) wsdlImports[i]).getStringValue();
          if (location != null) {
            if (!location.startsWith("file:") && location.indexOf("://") == -1) {
              location = joinRelativeUrl(wsdlUrl, location);
            }

            getSchemas(location, existing, loader, null);
          }
        }

        XmlObject[] wadl10Imports = xmlObject.selectPath("declare namespace s='"
            + Constants.WADL10_NS + "' .//s:grammars/s:include/@href");
        for (int i = 0; i < wadl10Imports.length; i++) {
          String location = ((SimpleValue) wadl10Imports[i]).getStringValue();
          if (location != null) {
            if (!location.startsWith("file:") && location.indexOf("://") == -1) {
              location = joinRelativeUrl(wsdlUrl, location);
            }

            getSchemas(location, existing, loader, null);
          }
        }

        XmlObject[] wadlImports = xmlObject.selectPath("declare namespace s='"
            + Constants.WADL11_NS + "' .//s:grammars/s:include/@href");
        for (int i = 0; i < wadlImports.length; i++) {
          String location = ((SimpleValue) wadlImports[i]).getStringValue();
          if (location != null) {
            if (!location.startsWith("file:") && location.indexOf("://") == -1) {
              location = joinRelativeUrl(wsdlUrl, location);
            }

            getSchemas(location, existing, loader, null);
          }
        }

      }

      existing.putAll(result);

      XmlObject[] schemas = result.values().toArray(new XmlObject[result.size()]);

      for (int c = 0; c < schemas.length; c++) {
        xmlObject = schemas[c];

        XmlObject[] schemaImports = xmlObject.selectPath("declare namespace s='" + Constants.XSD_NS
            + "' .//s:import/@schemaLocation");
        for (int i = 0; i < schemaImports.length; i++) {
          String location = ((SimpleValue) schemaImports[i]).getStringValue();
          Element elm = ((Attr) schemaImports[i].getDomNode()).getOwnerElement();

          if (location != null && !defaultSchemas.containsKey(elm.getAttribute("namespace"))) {
            if (!location.startsWith("file:") && location.indexOf("://") == -1) {
              location = joinRelativeUrl(wsdlUrl, location);
            }

            getSchemas(location, existing, loader, null);
          }
        }

        XmlObject[] schemaIncludes = xmlObject.selectPath("declare namespace s='"
            + Constants.XSD_NS + "' .//s:include/@schemaLocation");
        for (int i = 0; i < schemaIncludes.length; i++) {
          String location = ((SimpleValue) schemaIncludes[i]).getStringValue();
          if (location != null) {
            String targetNameSp = getTargetNamespace(xmlObject);

            if (!location.startsWith("file:") && location.indexOf("://") == -1) {
              location = joinRelativeUrl(wsdlUrl, location);
            }

            getSchemas(location, existing, loader, targetNameSp);
          }
        }
      }
    } catch (Exception e) {
      throw new SoapBuilderException(e);
    }
  }

  public static void getDefinitionParts(
      String origWsdlUrl,
      Map<String,
      XmlObject> existing,
      SchemaLoader loader)
      throws Exception {
    String wsdlUrl = origWsdlUrl;
    if (existing.containsKey(wsdlUrl)) {
      return;
    }

    XmlObject xmlObject = loader.loadXmlObject(wsdlUrl, null);
    existing.put(wsdlUrl, xmlObject);
    // wsdlUrl = loader.getBaseUri();

    selectDefinitionParts(wsdlUrl, existing, loader, xmlObject,
        "declare namespace s='" + Constants.WSDL11_NS + "' .//s:import/@location");
    selectDefinitionParts(wsdlUrl, existing, loader, xmlObject,
        "declare namespace s='" + Constants.WADL10_NS + "' .//s:grammars/s:include/@href");
    selectDefinitionParts(wsdlUrl, existing, loader, xmlObject,
        "declare namespace s='" + Constants.WADL11_NS + "' .//s:grammars/s:include/@href");
    selectDefinitionParts(wsdlUrl, existing, loader, xmlObject,
        "declare namespace s='" + Constants.XSD_NS + "' .//s:import/@schemaLocation");
    selectDefinitionParts(wsdlUrl, existing, loader, xmlObject,
        "declare namespace s='" + Constants.XSD_NS + "' .//s:include/@schemaLocation");
  }

  public static String joinRelativeUrl(String baseUrl, String url) {
    if (baseUrl.indexOf('?') > 0) {
      baseUrl = baseUrl.substring(0, baseUrl.indexOf('?'));
    }

    boolean isWindowsUrl = baseUrl.indexOf('\\') >= 0;
    boolean isUsedInUnix = File.separatorChar == '/';

    if (isUsedInUnix && isWindowsUrl) {
      baseUrl = baseUrl.replace('\\', '/');
      url = url.replace('\\', '/');
    }

    boolean isFile = baseUrl.startsWith("file:");

    int ix = baseUrl.lastIndexOf('\\');
    if (ix == -1) {
      ix = baseUrl.lastIndexOf('/');
    }

    // absolute?
    if (url.startsWith("/") && !isFile) {
      ix = baseUrl.indexOf("/", baseUrl.indexOf("//") + 2);
      return baseUrl.substring(0, ix) + url;
    }

    // remove leading "./"
    while (url.startsWith(".\\") || url.startsWith("./")) {
      url = url.substring(2);
    }

    // remove leading "../"
    while (url.startsWith("../") || url.startsWith("..\\")) {
      int ix2 = baseUrl.lastIndexOf('\\', ix - 1);
      if (ix2 == -1) {
        ix2 = baseUrl.lastIndexOf('/', ix - 1);
      }
      if (ix2 == -1) {
        break;
      }

      baseUrl = baseUrl.substring(0, ix2 + 1);
      ix = ix2;

      url = url.substring(3);
    }

    // remove "/./"
    while (url.indexOf("/./") != -1 || url.indexOf("\\.\\") != -1) {
      int ix2 = url.indexOf("/./");
      if (ix2 == -1) {
        ix2 = url.indexOf("\\.\\");
      }

      url = url.substring(0, ix2) + url.substring(ix2 + 2);
    }

    // remove "/../"
    while (url.indexOf("/../") != -1 || url.indexOf("\\..\\") != -1) {
      int ix2 = -1;

      int ix3 = url.indexOf("/../");
      if (ix3 == -1) {
        ix3 = url.indexOf("\\..\\");
        ix2 = url.lastIndexOf('\\', ix3 - 1);
      } else {
        ix2 = url.lastIndexOf('/', ix3 - 1);
      }

      if (ix2 == -1) {
        break;
      }

      url = url.substring(0, ix2) + url.substring(ix3 + 3);
    }

    String result = baseUrl.substring(0, ix + 1) + url;
    if (isFile) {
      result = result.replace('/', File.separatorChar);
    }

    return result;
  }

  private static void selectDefinitionParts(
      String wsdlUrl,
      Map<String,
      XmlObject> existing,
      SchemaLoader loader,
      XmlObject xmlObject,
      String path)
      throws Exception {
    XmlObject[] wsdlImports = xmlObject.selectPath(path);
    for (int i = 0; i < wsdlImports.length; i++) {
      String location = ((SimpleValue) wsdlImports[i]).getStringValue();
      if (location != null) {
        if (StringUtils.isNotBlank(location)) {
          if (!location.startsWith("file:") && location.indexOf("://") == -1) {
            location = joinRelativeUrl(wsdlUrl, location);
          }

          getDefinitionParts(location, existing, loader);
        } else {
          Node domNode = ((Attr) wsdlImports[i].getDomNode()).getOwnerElement();
          domNode.getParentNode().removeChild(domNode);
        }
      }
    }
  }

  /**
   * Used when creating a TypeSystem from a complete collection of
   * SchemaDocuments so that referenced types are not downloaded (again)
   */
  public static void removeImports(XmlObject xmlObject) throws XmlException {
    XmlObject[] imports = xmlObject.selectPath("declare namespace s='"
        + Constants.XSD_NS + "' .//s:import");

    for (int c = 0; c < imports.length; c++) {
      XmlCursor cursor = imports[c].newCursor();
      cursor.removeXml();
      cursor.dispose();
    }

    XmlObject[] includes = xmlObject.selectPath("declare namespace s='"
        + Constants.XSD_NS + "' .//s:include");

    for (int c = 0; c < includes.length; c++) {
      XmlCursor cursor = includes[c].newCursor();
      cursor.removeXml();
      cursor.dispose();
    }
  }

}
