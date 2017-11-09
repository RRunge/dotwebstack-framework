package org.dotwebstack.framework.frontend.ld;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.dotwebstack.framework.SparqlHttpStub;
import org.dotwebstack.framework.frontend.http.HttpConfiguration;
import org.dotwebstack.framework.test.DBEERPEDIA;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class LdIntegrationTest {
  private WebTarget target;

  @LocalServerPort
  private int port;

  @Autowired
  private HttpConfiguration httpConfiguration;

  @Before
  public void setUp() throws IOException {
    target = ClientBuilder.newClient(httpConfiguration).target(
        String.format("http://localhost:%d", this.port)).property(ClientProperties.FOLLOW_REDIRECTS,
            Boolean.FALSE);

    SparqlHttpStub.start();
  }

  @Test
  public void get_GetBreweryCollection_ThroughLdApi() {
    // Arrange
    Model model = new ModelBuilder().subject(DBEERPEDIA.BREWERIES).add(RDFS.LABEL,
        DBEERPEDIA.BREWERIES_LABEL).build();
    SparqlHttpStub.returnGraph(model);
    MediaType mediaType = MediaType.valueOf("text/turtle");

    // Act
    Response response = target.path("/dbp/ld/v1/graph-breweries").request().accept(mediaType).get();

    // Assert
    assertThat(response.getStatus(), equalTo(Status.OK.getStatusCode()));
    assertThat(response.getMediaType(), equalTo(mediaType));
    assertThat(response.getLength(), greaterThan(0));
    assertThat(response.readEntity(String.class),
        containsString(DBEERPEDIA.BREWERIES_LABEL.stringValue()));
  }

  @Test
  public void get_GetCorrectOptions_ThroughLdApi() {
    // Act
    Response response =
        target.path("/dbp/ld/v1/graph-breweries").request(MediaType.TEXT_PLAIN_TYPE).options();

    // Assert
    assertThat(response.getStatus(), equalTo(Status.OK.getStatusCode()));
    assertThat(response.getMediaType(), equalTo(MediaType.TEXT_PLAIN_TYPE));
    assertThat(response.readEntity(String.class), equalTo("HEAD, GET, OPTIONS"));
    assertThat(response.getHeaderString("allow"), equalTo("HEAD,GET,OPTIONS"));
  }

  @Test
  public void get_GetCorrectHead_ThroughLdApi() {
    // Arrange
    Model model = new ModelBuilder().subject(DBEERPEDIA.BREWERIES).add(RDFS.LABEL,
        DBEERPEDIA.BREWERIES_LABEL).build();
    SparqlHttpStub.returnGraph(model);

    // Act
    Response response =
        target.path("/dbp/ld/v1/graph-breweries").request("application/ld+json").head();

    // Assert
    assertThat(response.getStatus(), equalTo(Status.OK.getStatusCode()));
    assertThat(response.getMediaType(), equalTo(MediaType.valueOf("application/ld+json")));
    assertThat(response.getLength(), greaterThan(0));
    assertThat(response.readEntity(String.class), isEmptyString());
  }

  @Test
  public void get_GetRedirection_ThroughLdApi() throws URISyntaxException {
    // Act
    Response response = target.path("/dbp/ld/v1/id/breweries").request().get();

    // Assert
    assertThat(response.getStatus(), equalTo(Status.SEE_OTHER.getStatusCode()));
    assertThat(response.getLocation().getPath(), equalTo("/localhost/dbp/ld/v1/doc/breweries"));
    assertThat(response.readEntity(String.class), isEmptyString());
  }

  @Test
  public void get_ResourceNotFound_WhenResourceIsNotDefined() {
    // Act
    Response response = target.path("/dbp/ld/v1/foo").request().get();

    // Assert
    assertThat(response.getStatus(), equalTo(Status.NOT_FOUND.getStatusCode()));
  }

  @Test
  public void get_MethodNotAllowed_WhenDelete() {
    // Act
    Response response = target.path("/dbp/ld/v1/graph-breweries").request().delete();

    // Assert
    assertThat(response.getStatus(), equalTo(Status.METHOD_NOT_ALLOWED.getStatusCode()));
  }

  @Test
  public void get_NotAcceptable_WhenRequestingWrongMediaType() {
    // Act
    Response response =
        target.path("/dbp/ld/v1/graph-breweries").request(MediaType.APPLICATION_OCTET_STREAM).get();

    // Assert
    assertThat(response.getStatus(), equalTo(Status.NOT_ACCEPTABLE.getStatusCode()));
  }
}
