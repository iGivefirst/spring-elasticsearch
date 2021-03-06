/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package fr.pilato.spring.elasticsearch;

import fr.pilato.spring.elasticsearch.proxy.GenericInvocationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * A {@link FactoryBean} implementation used to create a {@link Node} element
 * which is an embedded instance of the cluster within a running application.
 * <p>
 * The lifecycle of the underlying {@link Node} instance is tied to the
 * lifecycle of the bean via the {@link #destroy()} method which calls
 * {@link Node#close()}
 * @see ElasticsearchAbstractFactoryBean
 * @author David Pilato
 */
public class ElasticsearchNodeFactoryBean extends ElasticsearchAbstractFactoryBean 
	implements FactoryBean<Node>,
		InitializingBean, DisposableBean {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private Node node;
	private Node proxyfiedNode;

	public void afterPropertiesSet() throws Exception {
		if (async) {
			Assert.notNull(taskExecutor);

			Future<Node> nodeFuture = taskExecutor.submit(new Callable<Node>() {
				public Node call() throws Exception {
					return initialize();
				}
			});
			proxyfiedNode = (Node) Proxy.newProxyInstance(Node.class.getClassLoader(),
					new Class[]{Node.class}, new GenericInvocationHandler(nodeFuture));

		} else {
			node = initialize();
		}
	}


	public void destroy() throws Exception {
		try {
			logger.info("Closing ElasticSearch node " + node.settings().get("name") );
			node.close();
		} catch (final Exception e) {
			logger.error("Error closing Elasticsearch node: ", e);
		}
	}

	public Node getObject() throws Exception {
		return async ? proxyfiedNode : node;
	}

	public Class<Node> getObjectType() {
		return Node.class;
	}

	public boolean isSingleton() {
		return true;
	}

	private Node initialize() {
		final NodeBuilder nodeBuilder = NodeBuilder.nodeBuilder();

		if (null != settings && null == properties) {
			logger.warn("settings has been deprecated in favor of properties. See issue #15: https://github.com/dadoonet/spring-elasticsearch/issues/15.");
			nodeBuilder.getSettings().put(settings);
		}

		if (null != settingsFile && null == properties) {
			Settings settings = ImmutableSettings.settingsBuilder()
					.loadFromClasspath(this.settingsFile)
					.build();
			nodeBuilder.getSettings().put(settings);
		}

		if (null != properties) {
			nodeBuilder.getSettings().put(properties);
		}

		if (logger.isDebugEnabled()) logger.debug("Starting ElasticSearch node...");
		node = nodeBuilder.node();
		logger.info("Node [" + node.settings().get("name") + "] for [" + node.settings().get("cluster.name") + "] cluster started...");
		if (logger.isDebugEnabled()) logger.debug("  - data : " + node.settings().get("path.data"));
		if (logger.isDebugEnabled()) logger.debug("  - logs : " + node.settings().get("path.logs"));

		return node;
	}

}
