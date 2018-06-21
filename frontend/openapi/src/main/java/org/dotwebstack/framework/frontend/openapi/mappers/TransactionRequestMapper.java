package org.dotwebstack.framework.frontend.openapi.mappers;

import com.atlassian.oai.validator.model.ApiOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response.Status;
import lombok.NonNull;
import org.dotwebstack.framework.config.ConfigurationException;
import org.dotwebstack.framework.frontend.openapi.OpenApiSpecificationExtensions;
import org.dotwebstack.framework.frontend.openapi.handlers.RequestHandlerFactory;
import org.dotwebstack.framework.transaction.Transaction;
import org.dotwebstack.framework.transaction.TransactionResourceProvider;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TransactionRequestMapper implements RequestMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionRequestMapper.class);

  private final TransactionResourceProvider transactionResourceProvider;

  private final RequestHandlerFactory requestHandlerFactory;

  private ValueFactory valueFactory = SimpleValueFactory.getInstance();

  @Autowired
  public TransactionRequestMapper(
      @NonNull TransactionResourceProvider transactionResourceProvider,
      @NonNull RequestHandlerFactory requestHandlerFactory) {
    this.transactionResourceProvider = transactionResourceProvider;
    this.requestHandlerFactory = requestHandlerFactory;
  }

  @Override
  public Boolean supportsVendorExtension(Map<String, Object> vendorExtensions) {
    return vendorExtensions.keySet().stream().anyMatch(
        key -> key.equals(OpenApiSpecificationExtensions.TRANSACTION));
  }

  @Override
  public void map(Resource.Builder resourceBuilder, OpenAPI openApi, ApiOperation apiOperation,
      Operation operation, String absolutePath) {
    String okStatusCode = Integer.toString(Status.OK.getStatusCode());

    if (operation.getResponses() == null
        || !operation.getResponses().containsKey(okStatusCode)) {
      throw new ConfigurationException(String.format(
          "Resource '%s' does not specify a status %s response.", absolutePath, okStatusCode));
    }

    Set<String> consumes =
        operation.getRequestBody() != null ? operation.getRequestBody().getContent().keySet()
            : null;

    if (consumes == null) {
      throw new ConfigurationException(
          String.format("Path '%s' should consume at least one media type.", absolutePath));
    }

    IRI transactionIdentifier =
        valueFactory.createIRI((String) operation.getExtensions().get(
            OpenApiSpecificationExtensions.TRANSACTION));

    Transaction transaction =
        transactionResourceProvider.get(transactionIdentifier);

    ResourceMethod.Builder methodBuilder =
        resourceBuilder.addMethod(apiOperation.getMethod().name()).handledBy(
            requestHandlerFactory.newTransactionRequestHandler(apiOperation, transaction, openApi));

    consumes.forEach(methodBuilder::consumes);

    if (LOG.isDebugEnabled()) {
      LOG.debug("Mapped {} operation for request path {}", apiOperation.getMethod().name(),
          absolutePath);
    }
  }

}
