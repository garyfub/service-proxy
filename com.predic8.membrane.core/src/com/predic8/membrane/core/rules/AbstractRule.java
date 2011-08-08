/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.rules;

import java.util.*;
import java.util.regex.Pattern;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.interceptor.cbr.XPathCBRInterceptor;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RegExURLRewriteInterceptor;
import com.predic8.membrane.core.interceptor.schemavalidation.SoapValidatorInterceptor;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.interceptor.statistics.*;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptor;

public abstract class AbstractRule extends AbstractConfigElement implements Rule {

	private static Log log = LogFactory.getLog(AbstractRule.class
			.getName());

	protected String name = ""; 
	 
	protected RuleKey key;
	
	protected boolean blockRequest;
	protected boolean blockResponse;
	
	protected boolean inboundTLS;
	
	protected boolean outboundTLS;
	
	protected List<Interceptor> interceptors = new ArrayList<Interceptor>();
	
	/**
	 * Used to determine the IP address for outgoing connections
	 */
	protected String localHost;

	/** 
	 * Map<Status Code, Count>
	 */
	private Map<Integer, Integer> statusCodes = new Hashtable<Integer, Integer>();
	
	private class InOutElement extends AbstractXmlElement {
		private Interceptor.Flow flow;

		protected void parseAttributes(XMLStreamReader token) throws Exception {
			if ("request".equals(token.getLocalName())) {
				flow = Flow.REQUEST;
			} else {
				flow = Flow.RESPONSE;
			}
		}
		
		@Override
		protected void parseChildren(XMLStreamReader token, String child)
				throws Exception {
			parseInterceptors(token, child).setFlow(flow);
		}
	}
	
	public AbstractRule() {
		super(null);
	}
	
	public AbstractRule(RuleKey ruleKey) {
		super(null);
		this.key = ruleKey;
	}
	
	protected abstract void parseKeyAttributes(XMLStreamReader token);
	
	@Override
	public String toString() { //TODO toString, getName, setName und name="" Initialisierung vereinheitlichen. 
		if (name != null && !"".equals(name))
			return name;
		
		return getKey().toString();
	}
	
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (LocalHost.ELEMENT_NAME.equals(child)) {
			this.localHost = ((LocalHost) (new LocalHost().parse(token))).getValue();
		} else if (Pattern.matches("request|response", child)){
			new InOutElement().parse(token);
		} else {
			parseInterceptors(token, child);
		}  
	}
	
	protected void writeExtension(XMLStreamWriter out)
	throws XMLStreamException {}	
	
	protected void writeRule(XMLStreamWriter out) throws XMLStreamException {
		
		out.writeAttribute("name", this.name);
		out.writeAttribute("port", "" + key.getPort());
		writeAttrIfTrue(out, blockRequest, "blockRequest", blockRequest);
		writeAttrIfTrue(out, blockResponse, "blockResponse", blockResponse);
		
		writeTLS(out);
		
		writeExtension(out);
		
		writeLocalHost(out);
		
		writeInterceptors(out);
		
	}
	
	private void writeLocalHost(XMLStreamWriter out) throws XMLStreamException {
		if (localHost == null)
			return;
		
		new LocalHost(localHost).write(out);
	}
	
	private Interceptor parseInterceptors(XMLStreamReader token, String child) throws Exception {
		Interceptor i = null;
		if ("interceptor".equals(child)) {
			i = getInterceptorBId(readInterceptor(token).getId());
		} else if ("adminConsole".equals(child)) {
			super.parseChildren(token,child); //ignores element
			return addAdminAndWebServerInterceptor(token);
		} else {
			i = getInlinedInterceptor(token, child);
		}		
		interceptors.add(i);
		return i;
	}

	private Interceptor addAdminAndWebServerInterceptor(XMLStreamReader token) {
		Interceptor a = new AdminConsoleInterceptor();
		a.setRouter(router);
		interceptors.add(a);
		Interceptor i = new WebServerInterceptor();
		i.setRouter(router);
		interceptors.add(i);
		return a;
	}

	private AbstractInterceptor readInterceptor(XMLStreamReader token)
		throws Exception {
		return (AbstractInterceptor) (new AbstractInterceptor(router)).parse(token);
	}
	
	private Interceptor getInterceptorBId(String id) {
		return router.getInterceptorFor(id);
	}

	private Interceptor getInlinedInterceptor(XMLStreamReader token, String name ) throws Exception {
		AbstractInterceptor i = null;
		if ("transform".equals(name)) {
			i = new XSLTInterceptor();
		} else if ("counter".equals(name)) {
			i = new CountInterceptor();
		} else if ("webServer".equals(name)) {
			i = new WebServerInterceptor();
		} else if ("balancer".equals(name)) {
			i = new LoadBalancingInterceptor();
		} else if ("clusterNotification".equals(name)) {
			i = new ClusterNotificationInterceptor();
		} else if ("regExUrlRewriter".equals(name)) {
			i = new RegExURLRewriteInterceptor();
		} else if ("soapValidator".equals(name)) {
			i = new SoapValidatorInterceptor();
		} else if ("rest2Soap".equals(name)) {
			i = new REST2SOAPInterceptor();
		} else if ("basicAuthentication".equals(name)) {
			i = new BasicAuthenticationInterceptor();
		} else if ("regExReplacer".equals(name)) {
			i = new RegExReplaceInterceptor();
		} else if ("switch".equals(name)) {
			i = new XPathCBRInterceptor();
		} else if ("wsdlRewriter".equals(name)) {
			i = new WSDLInterceptor();
		} else if ("accessControl".equals(name)) {
			i = new AccessControlInterceptor();
		} else if ("statisticsCSV".equals(name)) {
			i = new StatisticsCSVInterceptor();
		} else if ("statisticsJDBC".equals(name)) {
			i = new StatisticsJDBCInterceptor();
		} else if ("exchangeStore".equals(name)) {
			i = new ExchangeStoreInterceptor();
		} else {
			throw new Exception("Unknown interceptor found: "+name);
		}
		i.setRouter(router);
		i.parse(token);
		return i;
	}	
	
	private void parseTLS(XMLStreamReader token) {
		inboundTLS = getBoolean(token, "inboundTLS");
		outboundTLS = getBoolean(token, "outboundTLS");
	}
	
	private void parseBlocking(XMLStreamReader token) {
		blockRequest = getBoolean(token, "blockRequest");
		blockResponse = getBoolean(token, "blockResponse");
	}
		
	private void writeInterceptors(XMLStreamWriter out) throws XMLStreamException {
		Flow lastFlow = Flow.REQUEST_RESPONSE;
		for (Interceptor i : interceptors){
			if (i.getFlow() != lastFlow) {
				if (lastFlow != Flow.REQUEST_RESPONSE) {
					out.writeEndElement();
					log.debug(lastFlow==Flow.REQUEST?"</request>":"</response>");
				}
				
				if (i.getFlow() == Flow.REQUEST) {
					out.writeStartElement("request");
					log.debug("<request>");
				} else if (i.getFlow() == Flow.RESPONSE) {
					out.writeStartElement("response");					
					log.debug("<response>");
				}
				lastFlow = i.getFlow();
			}
			log.debug(i.getFlow() +":"+ i.getDisplayName());
			i.write(out);			
		}
		if (lastFlow != Flow.REQUEST_RESPONSE) {
			out.writeEndElement();
			log.debug(lastFlow==Flow.REQUEST?"</request>":"</response>");
		}
//		for (Interceptor i : interceptors){
//			i.write(out);
//		}
	}
	
	private void writeTLS(XMLStreamWriter out) throws XMLStreamException {
		writeAttrIfTrue(out, inboundTLS, "inboundTLS", inboundTLS);
		writeAttrIfTrue(out, outboundTLS, "outboundTLS", outboundTLS);
	}	
	
	protected <E> void writeAttrIfTrue(XMLStreamWriter out, boolean exp, String n, E v) throws XMLStreamException {
		if (exp) {
			out.writeAttribute(n,""+v);
		}
	}	
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		name = token.getAttributeValue(Constants.NS_UNDEFINED, "name");
		parseKeyAttributes(token);
		parseTLS(token);
		parseBlocking(token);
	}

	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}

	public String getName() {
		return name;
	}

	public RuleKey getKey() {
		return key;
	}

	public boolean isBlockRequest() {
		return blockRequest;
	}

	public boolean isBlockResponse() {
		return blockResponse;
	}

	public void setName(String name) {
		if (name == null)
			return;
		this.name = name;

	}
	public void setKey(RuleKey ruleKey) {
		this.key = ruleKey;
	}

	public void setBlockRequest(boolean blockStatus) {
		this.blockRequest = blockStatus;
	}
	
	public void setBlockResponse(boolean blockStatus) {
		this.blockResponse = blockStatus;
	}

	public boolean isInboundTLS() {
		return inboundTLS;
	}
	
	public boolean isOutboundTLS() {
		return outboundTLS;
	}
	
	public void setInboundTLS(boolean status) {
		inboundTLS = status;	
	}
	
	public void setOutboundTLS(boolean status) {
		this.outboundTLS = status;
	}

	public String getLocalHost() {
		return localHost;
	}

	public void setLocalHost(String localHost) {
		this.localHost = localHost;
	}	
	
	public synchronized void addStatusCode(int code) {
		if ( !statusCodes.containsKey(code) ) {
			statusCodes.put(code, 0);
		}
		statusCodes.put(code, statusCodes.get(code) + 1 );			
	}

	public Map<Integer, Integer> getStatusCodes() {
		return statusCodes;
	}

	public synchronized int getCount() {
		int c = 0;
		for ( int i : statusCodes.values() ) {
			c += i;
		}			
		return c;
	}
	
}
