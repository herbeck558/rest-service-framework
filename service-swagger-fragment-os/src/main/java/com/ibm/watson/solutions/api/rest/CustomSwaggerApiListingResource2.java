/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.solutions.api.rest;

import io.swagger.jaxrs.listing.ApiListingResource;

import javax.ws.rs.Path;

// Using a different path since another rest class used the path of "/"
// This variant is used when running with swagger v1.5.8 or higher.
@Path("/swagger/swagger.{type:json|yaml}")
public class CustomSwaggerApiListingResource2 extends ApiListingResource {

}
