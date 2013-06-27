package de.decoit.visa.http.ajax.handlers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import de.decoit.visa.TEBackend;
import de.decoit.visa.enums.IOToolRequestStatus;
import de.decoit.visa.http.QueryString;
import de.decoit.visa.http.ajax.AJAXServer;


/**
 * This HttpHandler implementation handles requests to load a specific topology
 * from the IO-Tool.<br>
 * <br>
 * On success, a JSON object will be returned containing the JSON object of the
 * current topology (key: topology) and the return code and message of the
 * IO-Tool reponse (keys: returncode and message). The return code and message
 * keys will also be present in an 'ajaxGeneral' reply.<br>
 * <br>
 * Any exception thrown during the processing of the request will cause the
 * request to fail.<br>
 * <br>
 * Possible return messages of this handler are:<br>
 * - ajaxSuccess (success)<br>
 * - ajaxGeneral (general error, see message key for details)<br>
 * - ajaxException (exception caught and no recovery attempt made)<br>
 * - ajaxMissing (missing arguments)
 *
 * @author Thomas Rix
 * @see AJAXServer
 * @see DefaultHandler
 */
public class IOToolLoadTopoHandler extends DefaultHandler {
	private static Logger log = Logger.getLogger(IOToolLoadTopoHandler.class.getName());


	@Override
	public void handle(HttpExchange he) throws IOException {
		log.info(he.getRequestURI().toString());

		// Get the URI of the request and extract the query string from it
		QueryString queryParameters = new QueryString(he.getRequestURI());

		// Create String for the response
		String response = null;

		// Check if the query parameters are valid for this handler
		if(this.checkQueryParameters(queryParameters)) {
			// Any exception thrown during object creation will
			// cause failure of the AJAX request
			try {
				// Execute the request to the IO-Tool
				IOToolRequestStatus status = TEBackend.getIOConnector().requestTopology(queryParameters.get("id").get());

				JSONObject rv = new JSONObject();
				if(status == IOToolRequestStatus.SUCCESS) {
					Map<String, String> data = TEBackend.getIOConnector().getLastReturnData();

					// Wrap the RDF/XML information into an InputStream
					ByteArrayInputStream bais = new ByteArrayInputStream(data.get(queryParameters.get("id").get()).getBytes());

					// Read that InputStream into the RDFManager
					TEBackend.RDF_MANAGER.loadRDF(bais, true, queryParameters.get("id").get());

					TEBackend.TOPOLOGY_STORAGE.setTopologyID(queryParameters.get("id").get());

					rv.put("status", AJAXServer.AJAX_SUCCESS);
					rv.put("topology", TEBackend.TOPOLOGY_STORAGE.genTopologyJSON());
				}
				else if(status == IOToolRequestStatus.IOTOOL_BUSY) {
					rv.put("status", AJAXServer.AJAX_ERROR_IOTOOL_BUSY);
				}
				else {
					rv.put("status", AJAXServer.AJAX_ERROR_GENERAL);
				}

				rv.put("returncode", TEBackend.getIOConnector().getLastReturnCode());
				rv.put("message", TEBackend.getIOConnector().getLastReturnMsg());
				response = rv.toString();
			}
			catch(Throwable ex) {
				TEBackend.logException(ex, log);

				JSONObject rv = new JSONObject();
				try {
					rv.put("status", AJAXServer.AJAX_ERROR_EXCEPTION);
					rv.put("type", ex.getClass().getSimpleName());
					rv.put("message", ex.getMessage());
					rv.put("topology", TEBackend.TOPOLOGY_STORAGE.genTopologyJSON());
				}
				catch(JSONException exc) {
					/* Ignore */
				}

				response = rv.toString();
			}
		}
		else {
			JSONObject rv = new JSONObject();
			try {
				// Missing or malformed query string, set response to error code
				rv.put("status", AJAXServer.AJAX_ERROR_MISSING_ARGS);
			}
			catch(JSONException exc) {
				/* Ignore */
			}

			response = rv.toString();
		}

		// Send the response
		sendResponse(he, response);
	}


	/**
	 * Check if the provided QueryString object contains all keys required by
	 * this handler and if their values are valid.
	 *
	 * @param pQueryString A QueryString object with all query parameters of the
	 *            request
	 * @return true if no problems were found, false if something is missing or
	 *         malformed
	 */
	private boolean checkQueryParameters(QueryString pQueryString) {
		boolean rv = true;

		// The host key must be present and the value must not be empty
		if(rv && (!pQueryString.containsKey("id") || pQueryString.get("id").get().isEmpty())) {
			rv = false;
		}

		return rv;
	}
}
