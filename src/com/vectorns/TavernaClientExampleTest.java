package com.vectorns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.Test;

public class TavernaClientExampleTest {

	private static final String TAVERNA_REST_API_URL = "http://localhost:8080/taverna-server/rest";
	private static final String WORKFLOW_TEST_FILE = "WorkflowTesting1.t2flow";

	private String getFileOnString(final String filePath) throws IOException {
		final BufferedReader bufferReader = new BufferedReader(new FileReader(filePath));
		try {
			final StringBuilder sb = new StringBuilder();
			String line = bufferReader.readLine();

			while (line != null) {
				sb.append(line);
				sb.append("\n");
				line = bufferReader.readLine();
			}
			return sb.toString();
		} finally {
			bufferReader.close();
		}
	}

	@Before
	public void cleanServer() {
		final CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider
				.setCredentials(new AuthScope("localhost", 8080), new UsernamePasswordCredentials("taverna", "taverna"));
		final CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

		try {
			final HttpGet httpGet = new HttpGet(TAVERNA_REST_API_URL + "/runs");

			final CloseableHttpResponse response = httpClient.execute(httpGet);
			assertEquals(200, response.getStatusLine().getStatusCode());

			final String[] xmlParts = httpClient.execute(httpGet, new BasicResponseHandler()).split("/");
			for (int i = 0; i < xmlParts.length; i++) {
				if (xmlParts[i].equals("runs")) {
					final HttpDelete httpDelete = new HttpDelete(TAVERNA_REST_API_URL + "/runs/"
							+ xmlParts[i + 1].split("\"")[0]);
					httpClient.execute(httpDelete);
				}
			}
		} catch (IOException e) {
			fail();
		}
	}

	@Test
	public void testPostRuns() throws UnsupportedEncodingException, IOException, InterruptedException {
		final CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider
				.setCredentials(new AuthScope("localhost", 8080), new UsernamePasswordCredentials("taverna", "taverna"));
		final CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider)
				.setMaxConnTotal(100).setMaxConnPerRoute(50).build();

		// Step 1
		StringEntity reqEntity = new StringEntity(getFileOnString(WORKFLOW_TEST_FILE));
		reqEntity.setContentType("application/vnd.taverna.t2flow+xml");

		final HttpPost httpPost = new HttpPost(TAVERNA_REST_API_URL + "/runs");
		httpPost.setEntity(reqEntity);
		httpPost.setHeader("ContentType", "application/vnd.taverna.t2flow+xml");

		CloseableHttpResponse response = httpClient.execute(httpPost);
		assertEquals(201, response.getStatusLine().getStatusCode());

		// Step 2
		final File inputFile = new File(WORKFLOW_TEST_FILE);
		assertTrue(inputFile.exists());

		reqEntity = new StringEntity(getFileOnString("input.xml"));
		reqEntity.setContentType("application/xml");

		final String[] urlParts = response.getHeaders("Location")[0].getValue().split("/");
		final String workflowUUID = urlParts[urlParts.length - 1];

		final HttpPut httpPutValues = new HttpPut(TAVERNA_REST_API_URL + "/runs/" + workflowUUID + "/input/input/ID");
		httpPutValues.setEntity(reqEntity);
		httpPutValues.setHeader("ContentType", "application/xml");

		response = httpClient.execute(httpPutValues);
		assertEquals(200, response.getStatusLine().getStatusCode());

		// Step 3
		final HttpPut httpPutRun = new HttpPut(TAVERNA_REST_API_URL + "/runs/" + workflowUUID + "/status");
		httpPutRun.setEntity(new StringEntity("Operating"));
		httpPutRun.setHeader("ContentType", "text/plain");

		response = httpClient.execute(httpPutRun);
		assertEquals(200, response.getStatusLine().getStatusCode());

		// Step 4
		final HttpGet httpGetStatus = new HttpGet(TAVERNA_REST_API_URL + "/runs/" + workflowUUID + "/status");
		String workflowStatus;
		do {
			workflowStatus = httpClient.execute(httpGetStatus, new BasicResponseHandler());
			Thread.sleep(1000);
		} while (workflowStatus.equalsIgnoreCase("Operating"));

		// Step 5
		final HttpGet httpGetOutputDescription = new HttpGet(TAVERNA_REST_API_URL + "/runs/" + workflowUUID + "/wd/out");
		httpGetOutputDescription.setHeader("Accept", "application/xml");
		final String[] outputDirectoryParts = httpClient.execute(httpGetOutputDescription, new BasicResponseHandler())
				.split("/");
		for (int i = 0; i < outputDirectoryParts.length; i++) {
			if (outputDirectoryParts[i].equals("out")) {
				final HttpGet httpGetOutput = new HttpGet(TAVERNA_REST_API_URL + "/runs/" + workflowUUID + "/wd/out/"
						+ outputDirectoryParts[i + 1].split("\"")[0]);
				httpGetOutput.setHeader("Accept", "text/plain");
				System.out.println(httpClient.execute(httpGetOutput, new BasicResponseHandler()));
				return;
			}
		}

		fail();
	}

}
