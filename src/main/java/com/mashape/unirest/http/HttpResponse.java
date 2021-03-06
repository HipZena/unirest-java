/*
The MIT License

Copyright (c) 2013 Mashape (http://mashape.com)

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package com.mashape.unirest.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;

import com.mashape.unirest.http.utils.ResponseUtils;

public class HttpResponse<T> {

	private int statusCode;
	private String statusText;
	private Headers headers = new Headers();
	private InputStream rawBody;
	private T body;
	private HttpRequestBase requestObj;
	private InputStream inputStream;
	private Boolean pending = false;
	
	public Boolean isPending() {
		return pending;
	}
	
	public HttpResponse(HttpRequestBase requestObj, org.apache.http.HttpResponse response, Class<T> responseClass) {
		this(response, responseClass);
		this.requestObj = requestObj;
	}
	
	@SuppressWarnings("unchecked")
	public HttpResponse(org.apache.http.HttpResponse response, Class<T> responseClass) {
		HttpEntity responseEntity = response.getEntity();
		
		Header[] allHeaders = response.getAllHeaders();
		for(Header header : allHeaders) {
			String headerName = header.getName().toLowerCase();
			List<String> list = headers.get(headerName);
			if (list == null) list = new ArrayList<String>();
			list.add(header.getValue());
			headers.put(headerName, list);
		}
		StatusLine statusLine = response.getStatusLine();
		this.statusCode = statusLine.getStatusCode();
		this.statusText = statusLine.getReasonPhrase();
		
		if (responseEntity != null) {
			String charset = "UTF-8";
			
			Header contentType = responseEntity.getContentType();
			if (contentType != null) {
				String responseCharset = ResponseUtils.getCharsetFromContentType(contentType.getValue());
				if (responseCharset != null && !responseCharset.trim().equals("")) {
					charset = responseCharset;
				}
			}
		
			try {				
				try {
					InputStream responseInputStream = responseEntity.getContent();
					if (ResponseUtils.isGzipped(responseEntity.getContentEncoding())) {
						responseInputStream = new GZIPInputStream(responseEntity.getContent());
					}
					inputStream = responseInputStream;
				} catch (IOException e2) {
					throw new RuntimeException(e2);
				}
				pending = true;
				
				if (String.class.equals(responseClass)) {
					byte[] rawBody = ResponseUtils.getBytes(inputStream);
					this.rawBody = new ByteArrayInputStream(rawBody);
					this.body = (T) new String(rawBody, charset);
					EntityUtils.consume(responseEntity);
					inputStream.close();
					this.close();
				} else if (InputStream.class.equals(responseClass)) {					
					this.rawBody = inputStream;
					this.body = (T) this.rawBody;
				} else {
					throw new Exception("Unknown result type. Only String, JsonNode and InputStream are supported.");
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public int getStatus() {
		return statusCode;
	}
	
	public String getStatusText() {
		return statusText;
	}

	public Headers getHeaders() {
		return headers;
	}

	public InputStream getRawBody() {
		return rawBody;
	}

	public T getBody() {
		return body;
	}
	
	public HttpRequestBase getBaseRequest() {
		return requestObj;
	}

	public void close() {
		if(pending){
			try{
				if(inputStream != null)
					inputStream.close();				
			} catch(IOException ex) {
				//do nothing
			}
			finally{
				if(requestObj != null)
					requestObj.releaseConnection();
				pending = false;
			}
		}		
	}
}
