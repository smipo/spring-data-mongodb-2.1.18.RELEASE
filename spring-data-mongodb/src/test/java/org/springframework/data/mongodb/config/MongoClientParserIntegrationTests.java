/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.config;

import static org.hamcrest.collection.IsIterableContainingInOrder.*;
import static org.hamcrest.core.Is.*;
import static org.hamcrest.core.IsInstanceOf.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.util.MongoClientVersion;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;

/**
 * Integration tests for {@link MongoClientParser}.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public class MongoClientParserIntegrationTests {

	DefaultListableBeanFactory factory;
	BeanDefinitionReader reader;

	@Before
	public void setUp() {

		this.factory = new DefaultListableBeanFactory();
		this.reader = new XmlBeanDefinitionReader(factory);
	}

	@Test // DATAMONGO-1158
	public void createsMongoClientCorrectlyWhenGivenHostAndPort() {

		reader.loadBeanDefinitions(new ClassPathResource("namespace/mongoClient-bean.xml"));

		assertThat(factory.getBean("mongo-client-with-host-and-port"), instanceOf(MongoClient.class));
	}

	@Test // DATAMONGO-1158
	public void createsMongoClientWithOptionsCorrectly() {

		reader.loadBeanDefinitions(new ClassPathResource("namespace/mongoClient-bean.xml"));

		AbstractApplicationContext context = new GenericApplicationContext(factory);
		context.refresh();

		try {
			MongoClient client = context.getBean("mongo-client-with-options-for-write-concern-and-read-preference",
					MongoClient.class);

			assertThat(client.getReadPreference(), is(ReadPreference.secondary()));
			assertThat(client.getWriteConcern(), is(WriteConcern.NORMAL));
		} finally {
			context.close();
		}
	}

	@Test // DATAMONGO-1158
	public void createsMongoClientWithDefaultsCorrectly() {

		reader.loadBeanDefinitions(new ClassPathResource("namespace/mongoClient-bean.xml"));

		AbstractApplicationContext context = new GenericApplicationContext(factory);
		context.refresh();

		try {
			MongoClient client = context.getBean("mongoClient", MongoClient.class);

			assertThat(client.getAddress().getHost(), is("127.0.0.1"));
			assertThat(client.getAddress().getPort(), is(27017));
		} finally {
			context.close();
		}
	}

	@Test // DATAMONGO-1158
	public void createsMongoClientWithCredentialsCorrectly() {

		reader.loadBeanDefinitions(new ClassPathResource("namespace/mongoClient-bean.xml"));

		AbstractApplicationContext context = new GenericApplicationContext(factory);
		context.refresh();

		try {
			MongoClient client = context.getBean("mongo-client-with-credentials", MongoClient.class);

			assertThat(client.getCredentialsList(),
					contains(MongoCredential.createPlainCredential("jon", "snow", "warg".toCharArray())));
		} finally {
			context.close();
		}
	}

	@Test // DATAMONGO-1620
	public void createsMongoClientWithServerSelectionTimeoutCorrectly() {

		reader.loadBeanDefinitions(new ClassPathResource("namespace/mongoClient-bean.xml"));

		AbstractApplicationContext context = new GenericApplicationContext(factory);
		context.refresh();

		try {

			MongoClient client = context.getBean("mongo-client-with-server-selection-timeout", MongoClient.class);
			assertThat(client.getMongoClientOptions().getServerSelectionTimeout(), is((Object) 100));
		} finally {
			context.close();
		}
	}
}
