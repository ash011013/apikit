/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.apikit.validation;

import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.module.apikit.ApikitErrorTypes;
import org.mule.module.apikit.exception.BadRequestException;
import org.mule.module.apikit.exception.UnsupportedMediaTypeException;
import org.mule.module.apikit.helpers.AttributesHelper;
import org.mule.module.apikit.helpers.PayloadHelper;
import org.mule.module.apikit.validation.body.schema.RestSchemaValidator;
import org.mule.module.apikit.validation.body.schema.v1.RestJsonSchemaValidator;
import org.mule.module.apikit.validation.body.schema.v1.RestXmlSchemaValidator;
import org.mule.module.apikit.validation.body.schema.v1.cache.SchemaCacheUtils;
import org.mule.module.apikit.validation.body.schema.v2.RestSchemaV2Validator;
import org.mule.raml.interfaces.model.IAction;
import org.mule.raml.interfaces.model.IMimeType;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BodyValidator {

  protected final static Logger logger = LoggerFactory.getLogger(BodyValidator.class);


  public static ValidBody validate(IAction action, HttpRequestAttributes attributes, Object payload,
                                ValidationConfig config, String charset)
      throws BadRequestException {

    ValidBody validBody = new ValidBody(payload);

    if (action == null || !action.hasBody()) {
      logger.debug("=== no body types defined: accepting any request content-type");
      return validBody;
    }

    String requestMimeTypeName = AttributesHelper.getMediaType(attributes);
    Map.Entry<String, IMimeType> foundMimeType;

    try {

      foundMimeType = action.getBody()
          .entrySet()
          .stream()
          .filter(entry -> {
            if (logger.isDebugEnabled()) {
              logger.debug(String.format("comparing request media type %s with expected %s\n", requestMimeTypeName,
                                         entry.getKey()));
            }

            return entry.getKey().equals(requestMimeTypeName);
          })
          .findFirst()
          .get();

    } catch (NoSuchElementException e) {
      throw ApikitErrorTypes.throwErrorType(new UnsupportedMediaTypeException());
    }


    IMimeType mimeType = foundMimeType.getValue();


    if(requestMimeTypeName.contains("json") || requestMimeTypeName.contains("xml")) {

      validateAsString(config, mimeType, action, requestMimeTypeName, payload, charset);

    }

    return validBody;
  }

  private static void validateAsString(ValidationConfig config, IMimeType mimeType, IAction action, String requestMimeTypeName, Object payload, String charset) throws BadRequestException {
    RestSchemaValidator schemaValidator = null;

    if (config.isParserV2()) {
      schemaValidator = new RestSchemaValidator(new RestSchemaV2Validator(mimeType));
    } else {
      String schemaPath = SchemaCacheUtils.getSchemaCacheKey(action, requestMimeTypeName);

      try {
        if (requestMimeTypeName.contains("json")) {

          schemaValidator = new RestSchemaValidator(new RestJsonSchemaValidator(config.getJsonSchema(schemaPath).getSchema()));

        } else if(requestMimeTypeName.contains("xml")) {
          schemaValidator = new RestSchemaValidator(new RestXmlSchemaValidator(config.getXmlSchema(schemaPath)));
        }
      } catch (ExecutionException e) {
        throw ApikitErrorTypes.throwErrorType(new BadRequestException(e));
      }
    }


    String strPayload = PayloadHelper.getPayloadAsString(payload, charset, requestMimeTypeName.contains("json"));

    schemaValidator.validate(strPayload);

  }

}