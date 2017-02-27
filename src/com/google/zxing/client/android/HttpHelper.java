/*
 * Copyright 2011 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.util.Log;

/**
 * Utility methods for retrieving content over HTTP using the more-supported
 * {@code java.net} classes in Android.
 */
public final class HttpHelper {

	
	public static String TAG_HOST = "http://192.168.0.106:63627";
	public static String TAG_LOGIN = "/api/GetStudentByName";
	public static String TAG_LESSON = "/api/GetAllLessonByStudentId";
	public static String TAG_GROUP = "/api/GetAllGroupByLessonId";
	public static String TAG_VOCABULARY	 = "/api/GetAllVocabularyByGroupId";
	public static String TAG_IMG = "/img/";
	public static String TAG_GET_GROUP_WORD= "/api/GetGroupVocabularyByImageId";
	public static String TAG_SEND_RESULT= "/api/UpdateResult";
	public static String TAG_SEND_FEEDBACKS= "/api/StudentLikeLesson";
	public static String TAG_IMAGE_URL = TAG_HOST+"/img/vocabulary/";
	public static int LOAD_WORD_DONE = 100;
	public static int LOAD_WORD_NETWORD_ERROR = 101;
	public static int LOAD_WORD_ERROR = 102;
	public static int LOAD_ERROR = 103;
	public static int UPDATED_RESULT = 104;
	public static String TAG_ID = "id";
	public static String TAG_WORD = "word";
	public static String TAG_IMAGE = "image";
	
	static String response = null;
	public final static int GET = 1;
	public final static int POST = 2;

	private static final String TAG = HttpHelper.class.getSimpleName();

	private static final Collection<String> REDIRECTOR_DOMAINS = new HashSet<String>(
			Arrays.asList("amzn.to", "bit.ly", "bitly.com", "fb.me", "goo.gl",
					"is.gd", "j.mp", "lnkd.in", "ow.ly", "R.BEETAGG.COM",
					"r.beetagg.com", "SCN.BY", "su.pr", "t.co", "tinyurl.com",
					"tr.im"));

	private HttpHelper() {
	}

	public enum ContentType {
		/** HTML-like content type, including HTML, XHTML, etc. */
		HTML,
		/** JSON content */
		JSON,
		/** XML */
		XML,
		/** Plain text content */
		TEXT,
	}

	/**
	 * Downloads the entire resource instead of part.
	 * 
	 * @param uri
	 *            URI to retrieve
	 * @param type
	 *            expected text-like MIME type of that content
	 * @return content as a {@code String}
	 * @throws IOException
	 *             if the content can't be retrieved because of a bad URI,
	 *             network problem, etc.
	 * @see #downloadViaHttp(String, HttpHelper.ContentType, int)
	 */
	public static CharSequence downloadViaHttp(String uri, ContentType type)
			throws IOException {
		return downloadViaHttp(uri, type, Integer.MAX_VALUE);
	}

	/**
	 * @param uri
	 *            URI to retrieve
	 * @param type
	 *            expected text-like MIME type of that content
	 * @param maxChars
	 *            approximate maximum characters to read from the source
	 * @return content as a {@code String}
	 * @throws IOException
	 *             if the content can't be retrieved because of a bad URI,
	 *             network problem, etc.
	 */
	public static CharSequence downloadViaHttp(String uri, ContentType type,
			int maxChars) throws IOException {
		String contentTypes;
		switch (type) {
		case HTML:
			contentTypes = "application/xhtml+xml,text/html,text/*,*/*";
			break;
		case JSON:
			contentTypes = "application/json,text/*,*/*";
			break;
		case XML:
			contentTypes = "application/xml,text/*,*/*";
			break;
		case TEXT:
		default:
			contentTypes = "text/*,*/*";
		}
		return downloadViaHttp(uri, contentTypes, maxChars);
	}

	private static CharSequence downloadViaHttp(String uri,
			String contentTypes, int maxChars) throws IOException {
		int redirects = 0;
		while (redirects < 5) {
			URL url = new URL(uri);
			HttpURLConnection connection = safelyOpenConnection(url);
			connection.setInstanceFollowRedirects(true); // Won't work HTTP ->
															// HTTPS or vice
															// versa
			connection.setRequestProperty("Accept", contentTypes);
			connection.setRequestProperty("Accept-Charset", "utf-8,*");
			connection.setRequestProperty("User-Agent", "ZXing (Android)");
			
			try {
				int responseCode = safelyConnect(connection);
				switch (responseCode) {
				case HttpURLConnection.HTTP_OK:
					return consume(connection, maxChars);
				case HttpURLConnection.HTTP_MOVED_TEMP:
					String location = connection.getHeaderField("Location");
					if (location != null) {
						uri = location;
						redirects++;
						continue;
					}
					throw new IOException("No Location");
				default:
					throw new IOException("Bad HTTP response: " + responseCode);
				}
			} finally {
				connection.disconnect();
			}
		}
		throw new IOException("Too many redirects");
	}

	private static String getEncoding(URLConnection connection) {
		String contentTypeHeader = connection.getHeaderField("Content-Type");
		if (contentTypeHeader != null) {
			int charsetStart = contentTypeHeader.indexOf("charset=");
			if (charsetStart >= 0) {
				return contentTypeHeader.substring(charsetStart
						+ "charset=".length());
			}
		}
		return "UTF-8";
	}

	private static CharSequence consume(URLConnection connection, int maxChars)
			throws IOException {
		String encoding = getEncoding(connection);
		StringBuilder out = new StringBuilder();
		Reader in = null;
		try {
			in = new InputStreamReader(connection.getInputStream(), encoding);
			char[] buffer = new char[1024];
			int charsRead;
			while (out.length() < maxChars && (charsRead = in.read(buffer)) > 0) {
				out.append(buffer, 0, charsRead);
			}
		} finally {
			if (in != null) {
				in.close();

			}
		}
		return out;
	}

	public static URI unredirect(URI uri) throws IOException {
		if (!REDIRECTOR_DOMAINS.contains(uri.getHost())) {
			return uri;
		}
		URL url = uri.toURL();
		HttpURLConnection connection = safelyOpenConnection(url);
		connection.setInstanceFollowRedirects(false);
		connection.setDoInput(false);
		connection.setRequestMethod("HEAD");
		connection.setRequestProperty("User-Agent", "ZXing (Android)");
		try {
			int responseCode = safelyConnect(connection);
			switch (responseCode) {
			case HttpURLConnection.HTTP_MULT_CHOICE:
			case HttpURLConnection.HTTP_MOVED_PERM:
			case HttpURLConnection.HTTP_MOVED_TEMP:
			case HttpURLConnection.HTTP_SEE_OTHER:
			case 307: // No constant for 307 Temporary Redirect ?
				String location = connection.getHeaderField("Location");
				if (location != null) {
					try {
						return new URI(location);
					} catch (URISyntaxException e) {
						// nevermind
					}
				}
			}
			return uri;
		} finally {
			connection.disconnect();
		}
	}

	private static HttpURLConnection safelyOpenConnection(URL url)
			throws IOException {
		URLConnection conn;
		try {
			conn = url.openConnection();
		} catch (NullPointerException npe) {
			// Another strange bug in Android?
			Log.w(TAG, "Bad URI? " + url);
			throw new IOException(npe);
		}
		if (!(conn instanceof HttpURLConnection)) {
			throw new IOException();
		}
		return (HttpURLConnection) conn;
	}

	private static int safelyConnect(HttpURLConnection connection)
			throws IOException {
		try {
			connection.connect();
		} catch (Exception e) {
			// this is an Android bug:
			// http://code.google.com/p/android/issues/detail?id=16895
			throw new IOException(e);
		}
		try {
			return connection.getResponseCode();
		} catch (Exception e) {
			// this is maybe this Android bug:
			// http://code.google.com/p/android/issues/detail?id=15554
			throw new IOException(e);
		}
	}

	/*
	 * Making server call
	 * 
	 * @url - url to make request
	 * 
	 * @method - http request method
	 * 
	 * @params - http request params
	 */
	public static String makeServerCall(String url, int method,
			List<NameValuePair> params) {
		try {
			// http client
			DefaultHttpClient httpClient = new DefaultHttpClient();
			HttpEntity httpEntity = null;
			HttpResponse httpResponse = null;

			// Checking http request method type
			if (method == POST) {
				HttpPost httpPost = new HttpPost(url);
				httpPost.addHeader("Content-Type" , "application/json; charset=utf-8");
			
				// adding post params
				if (params != null) {
					httpPost.setEntity(new UrlEncodedFormEntity(params));
				}

				httpResponse = httpClient.execute(httpPost);

			} else if (method == GET) {
				// appending params to url
				if (params != null) {
					String paramString = URLEncodedUtils
							.format(params, "utf-8");
					url += "?" + paramString;
				}
				HttpGet httpGet = new HttpGet(url);

				httpResponse = httpClient.execute(httpGet);

			}
			httpEntity = httpResponse.getEntity();
			response = EntityUtils.toString(httpEntity);

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return response;

	}

}
