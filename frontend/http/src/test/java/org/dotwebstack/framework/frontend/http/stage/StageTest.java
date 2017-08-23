package org.dotwebstack.framework.frontend.http.stage;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.dotwebstack.framework.frontend.http.site.Site;
import org.dotwebstack.framework.test.DBEERPEDIA;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StageTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  Site siteMock;

  @Test
  public void builder() {
    //Act
    Stage stage = new Stage.Builder(DBEERPEDIA.BREWERIES, siteMock)
        .basePath(DBEERPEDIA.BASE_PATH.stringValue())
        .build();

    // Assert
    assertThat(stage.getIdentifier(), equalTo(DBEERPEDIA.BREWERIES));
    assertThat(stage.getSite(), equalTo(siteMock));
    assertThat(stage.getBasePath(), equalTo(DBEERPEDIA.BASE_PATH.stringValue()));
  }

  @Test
  public void builderWithDefaultValues() {
    //Act
    Stage stage = new Stage.Builder(DBEERPEDIA.BREWERIES, siteMock).build();

    // Assert
    assertThat(stage.getIdentifier(), equalTo(DBEERPEDIA.BREWERIES));
    assertThat(stage.getSite(), equalTo(siteMock));
    assertThat(stage.getBasePath(), equalTo(Stage.DEFAULT_BASE_PATH));
  }

  public void buildWithMandatoryNullValues1() {
    // Assert
    thrown.expect(NullPointerException.class);

    // Act
    Stage stage = new Stage.Builder(null, null).build();
  }

  public void buildWithMandatoryNullValues2() {
    // Assert
    thrown.expect(NullPointerException.class);

    // Act
    Stage stage = new Stage.Builder(DBEERPEDIA.BREWERIES, null).build();
  }

  public void buildWithOptionalNullValues() {
    // Assert
    thrown.expect(NullPointerException.class);

    // Act
    Stage stage = new Stage.Builder(DBEERPEDIA.BREWERIES, siteMock).basePath(null).build();
  }

}