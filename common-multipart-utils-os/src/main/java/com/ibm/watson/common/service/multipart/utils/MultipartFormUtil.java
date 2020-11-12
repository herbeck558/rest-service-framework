/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.multipart.utils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.activation.DataHandler;
import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.websphere.jaxrs20.multipart.IAttachment;
import com.ibm.websphere.jaxrs20.multipart.IMultipartBody;

/**
 * This is a utility to be used when attaching files for a REST service using multipart forms with JAX-RS 2.0.
 * Here's the steps to use this utility
 *
 * Add a JAX-RS API that includes multipart/form-data.  For example:
 *
 * 	 @Consumes({"multipart/form-data"})
 *
 * The body parameter must be defined using ApiImplicitParams with a dataType of java.io.File.  e.g:
 *
 * 	 @ApiImplicitParams({@ApiImplicitParam (name="file", value="Attached file", dataType="java.io.File", paramType="form")})
 *
 * If your form supports name / value pair form parameters too, add those as additional form parameters:
 *
 *	 @ApiImplicitParam (name="owner", value="Owner", dataType="string", paramType="form"),
 *
 * The method should take a request body parameter defined as (plus any QueryParams or PathParams you may have):
 *
 *   @ApiParam(hidden=true) IMultipartBody multipartBody,
 *
 * Use this utility within a try block so that we can close all the input streams automatically.  Use the getFormFields
 * to get the name/value pairs. Use getFormFiles to work with the attached files -- the keys will be the file names.
 *
 *	 try (MultipartFormUtil formUtil = new MultipartFormUtil(multipartBody)) {
 * 	  	 Map<String,String> formFields = formUtil.getFormFields();
 * 	   	 Map<String,InputStream> formFiles = formUtil.getFormFiles();
 *   }
 *
 *
 *
 */
public class MultipartFormUtil implements Closeable {
	protected static final Logger logger = LoggerFactory.getLogger(MultipartFormUtil.class.getName());
	protected Map<String,String> formContentType = new HashMap<String,String>();
	protected Map<String,String> formFields = new HashMap<String,String>();
	protected Map<String,InputStream> formFiles = new HashMap<String,InputStream>();

	public MultipartFormUtil(IMultipartBody multipartBody) throws IOException {
		init(multipartBody);
	}

	public void setMultipartBody(IMultipartBody multipartBody) throws IOException {
		init(multipartBody);
	}

	private void init(IMultipartBody multipartBody) throws IOException {
		 formFields = new HashMap<String,String>();
		 formFiles = new HashMap<String,InputStream>();
		 int count = 0;
		 List <IAttachment> attachments = multipartBody.getAllAttachments();
		 String formElementValue = null;
		 InputStream stream = null;
		 for (Iterator<IAttachment> it = attachments.iterator(); it.hasNext();) {
			 IAttachment attachment = it.next();
			 if (attachment == null) {
				 continue;
			 }
			 DataHandler dataHandler = attachment.getDataHandler();
			 stream = dataHandler.getInputStream();

			 MultivaluedMap<String, String> map = attachment.getHeaders();
			 String fileName = null;
			 String formElementName = null;

			 String contentType = map.containsKey("Content-Type") ? map.getFirst("Content-Type") : null;

			 String sContentDisp="Content-Disposition";
			 if( map.containsKey(sContentDisp) ) {
				 String[] contentDisposition = map.getFirst(sContentDisp).split(";");
				 for (String tempName : contentDisposition) {
					 String[] names = tempName.split("=");
					 if (names.length <= 1) continue;
					 formElementName = names[1].trim().replaceAll("\"", "");
					 if ((tempName.trim().startsWith("filename"))) {
						 fileName = formElementName;
					 }
				 }
			 }
			 if (fileName == null) {
				 StringBuffer sb = new StringBuffer();
				 BufferedReader br = new BufferedReader(new InputStreamReader(stream));
				 String line = null;
				 try {
					 while ((line = br.readLine()) != null) {
						 sb.append(line + "\n");
					 }
				 } catch (IOException e) {
					 e.printStackTrace();
				 } finally {
					 if (br != null) {
						 try {
							 br.close();
						 } catch (IOException e) {
							 e.printStackTrace();
						 }
					 }
				 }
				 formElementValue = sb.toString().trim();
				 formFields.put(formElementName, formElementValue);
				 if (contentType != null) {
					 formContentType.put(formElementName, contentType);
				 } else {
					 if (logger.isDebugEnabled()) logger.debug(formElementName + ":" + formElementValue);
				 }

			 } else {
				 formFiles.put(fileName,stream);
				 if (contentType != null) formContentType.put(fileName, contentType);
			 }
		 }
	}

	public Map<String,String> getFormContentType() { return formContentType; }

	public Map<String,String> getFormFields() { return formFields; }

	public Map<String,InputStream> getFormFiles() { return formFiles; }

	@Override
	public void close() throws IOException {
		for (String key : formFiles.keySet()) {
			try {
				formFiles.get(key).close();
			} catch(Exception e) {}
		}
	}

}
