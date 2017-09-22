package org.dotwebstack.framework.frontend.http.provider.graph;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import javax.ws.rs.core.MediaType;
import org.dotwebstack.framework.frontend.http.provider.MediaTypes;
import org.dotwebstack.framework.test.DBEERPEDIA;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.impl.BackgroundGraphResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JsonLdGraphMessageBodyWriterTest {

  @Mock
  private OutputStream outputStream;

  @Mock
  private GraphQueryResult graphQueryResult;

  @Captor
  private ArgumentCaptor<byte[]> byteCaptor;


  @Test
  public void isWritable_IsTrue_ForJsonLdMediaType() {
    // Arrange
    JsonLdGraphMessageBodyWriter writer = new JsonLdGraphMessageBodyWriter();

    // Act
    boolean result =
        writer.isWriteable(BackgroundGraphResult.class, null, null, MediaTypes.LDJSON_TYPE);

    // Assert
    assertThat(result, is(true));
  }

  @Test
  public void isWritable_IsFalse_ForStringClass() {
    // Arrange
    JsonLdGraphMessageBodyWriter writer = new JsonLdGraphMessageBodyWriter();

    // Act
    boolean result = writer.isWriteable(String.class, null, null, MediaTypes.LDJSON_TYPE);

    // Assert
    assertThat(result, is(false));
  }

  @Test
  public void isWritable_IsFalse_ForXmlMediaType() {
    // Arrange
    JsonLdGraphMessageBodyWriter writer = new JsonLdGraphMessageBodyWriter();

    // Act
    boolean result = writer.isWriteable(String.class, null, null, MediaType.APPLICATION_XML_TYPE);

    // Assert
    assertThat(result, is(false));
  }

  @Test
  public void getSize_MinusOne_Always() {
    // Arrange
    JsonLdGraphMessageBodyWriter writer = new JsonLdGraphMessageBodyWriter();

    // Act
    long result =
        writer.getSize(graphQueryResult, null, null, null, MediaType.APPLICATION_XML_TYPE);

    // Assert
    assertThat(result, equalTo(-1L));
  }

  @Test
  public void writeTo_JsonLdFormat_ForQueryResult() throws Exception {
    // Arrange
    JsonLdGraphMessageBodyWriter writer = new JsonLdGraphMessageBodyWriter();
    Model model =
        new ModelBuilder().subject(DBEERPEDIA.BREWERIES).add(RDF.TYPE, DBEERPEDIA.BACKEND).add(
            RDFS.LABEL, DBEERPEDIA.BREWERIES_LABEL).build();

    when(graphQueryResult.hasNext()).thenReturn(true, true, true, false);
    when(graphQueryResult.next()).thenReturn(model.stream().findFirst().get(),
        model.stream().skip(1).toArray(Statement[]::new));

    // Act
    writer.writeTo(graphQueryResult, null, null, null, null, null, outputStream);

    // Assert
    verify(outputStream).write(byteCaptor.capture(), anyInt(), anyInt());
    String result = new String(byteCaptor.getValue());
    assertThat(result, containsString(
        "[{\"@id\":\"http://dbeerpedia.org#Breweries\",\"@type\":[\"http://dbeerpedia.org#Backend\"]"));
    assertThat(result, containsString(
        "http://www.w3.org/2000/01/rdf-schema#label\":[{\"@value\":\"Beer breweries in The Netherlands\"}]}]"));
  }

}
