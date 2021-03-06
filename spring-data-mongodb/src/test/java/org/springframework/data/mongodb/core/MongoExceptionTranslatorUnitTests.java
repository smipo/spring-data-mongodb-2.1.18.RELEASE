/*
 * Copyright 2013-2020 the original author or authors.
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
package org.springframework.data.mongodb.core;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.net.UnknownHostException;

import org.bson.BsonDocument;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.mongodb.ClientSessionException;
import org.springframework.data.mongodb.MongoTransactionException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;

import com.mongodb.MongoCursorNotFoundException;
import com.mongodb.MongoException;
import com.mongodb.MongoInternalException;
import com.mongodb.MongoSocketException;
import com.mongodb.ServerAddress;

/**
 * Unit tests for {@link MongoExceptionTranslator}.
 *
 * @author Michal Vich
 * @author Oliver Gierke
 * @author Christoph Strobl
 */
public class MongoExceptionTranslatorUnitTests {

	MongoExceptionTranslator translator;

	@Before
	public void setUp() {
		translator = new MongoExceptionTranslator();
	}

	@Test
	public void translateDuplicateKey() {

		expectExceptionWithCauseMessage(
				translator.translateExceptionIfPossible(
						new com.mongodb.DuplicateKeyException(new BsonDocument(), new ServerAddress(), null)),
				DuplicateKeyException.class, null);
	}

	@Test
	public void translateSocketException() {

		expectExceptionWithCauseMessage(
				translator.translateExceptionIfPossible(new MongoSocketException("IOException", new ServerAddress())),
				DataAccessResourceFailureException.class, "IOException");

	}

	@Test
	public void translateCursorNotFound() throws UnknownHostException {

		expectExceptionWithCauseMessage(
				translator.translateExceptionIfPossible(new MongoCursorNotFoundException(1L, new ServerAddress())),
				DataAccessResourceFailureException.class);
	}

	@Test
	public void translateToDuplicateKeyException() {

		checkTranslatedMongoException(DuplicateKeyException.class, 11000);
		checkTranslatedMongoException(DuplicateKeyException.class, 11001);
	}

	@Test
	public void translateToDataAccessResourceFailureException() {

		checkTranslatedMongoException(DataAccessResourceFailureException.class, 12000);
		checkTranslatedMongoException(DataAccessResourceFailureException.class, 13440);
	}

	@Test
	public void translateToInvalidDataAccessApiUsageException() {

		checkTranslatedMongoException(InvalidDataAccessApiUsageException.class, 10003);
		checkTranslatedMongoException(InvalidDataAccessApiUsageException.class, 12001);
		checkTranslatedMongoException(InvalidDataAccessApiUsageException.class, 12010);
		checkTranslatedMongoException(InvalidDataAccessApiUsageException.class, 12011);
		checkTranslatedMongoException(InvalidDataAccessApiUsageException.class, 12012);
	}

	@Test
	public void translateToUncategorizedMongoDbException() {

		MongoException exception = new MongoException(0, "");
		DataAccessException translatedException = translator.translateExceptionIfPossible(exception);

		expectExceptionWithCauseMessage(translatedException, UncategorizedMongoDbException.class);
	}

	@Test
	public void translateMongoInternalException() {

		MongoInternalException exception = new MongoInternalException("Internal exception");
		DataAccessException translatedException = translator.translateExceptionIfPossible(exception);

		expectExceptionWithCauseMessage(translatedException, InvalidDataAccessResourceUsageException.class);
	}

	@Test
	public void translateUnsupportedException() {

		RuntimeException exception = new RuntimeException();
		assertThat(translator.translateExceptionIfPossible(exception), is(nullValue()));
	}

	@Test // DATAMONGO-2045
	public void translateSessionExceptions() {

		checkTranslatedMongoException(ClientSessionException.class, 206);
		checkTranslatedMongoException(ClientSessionException.class, 213);
		checkTranslatedMongoException(ClientSessionException.class, 228);
		checkTranslatedMongoException(ClientSessionException.class, 264);
	}

	@Test // DATAMONGO-2045
	public void translateTransactionExceptions() {

		checkTranslatedMongoException(MongoTransactionException.class, 217);
		checkTranslatedMongoException(MongoTransactionException.class, 225);
		checkTranslatedMongoException(MongoTransactionException.class, 244);
		checkTranslatedMongoException(MongoTransactionException.class, 251);
		checkTranslatedMongoException(MongoTransactionException.class, 256);
		checkTranslatedMongoException(MongoTransactionException.class, 257);
		checkTranslatedMongoException(MongoTransactionException.class, 263);
		checkTranslatedMongoException(MongoTransactionException.class, 267);
	}

	private void checkTranslatedMongoException(Class<? extends Exception> clazz, int code) {

		DataAccessException translated = translator.translateExceptionIfPossible(new MongoException(code, ""));

		assertThat("Expected exception of type " + clazz.getName() + "!", translated, is(not(nullValue())));

		Throwable cause = translated.getRootCause();
		assertThat(cause, is(instanceOf(MongoException.class)));
		assertThat(((MongoException) cause).getCode(), is(code));
	}

	private static void expectExceptionWithCauseMessage(NestedRuntimeException e,
			Class<? extends NestedRuntimeException> type) {
		expectExceptionWithCauseMessage(e, type, null);
	}

	private static void expectExceptionWithCauseMessage(NestedRuntimeException e,
			Class<? extends NestedRuntimeException> type, String message) {

		assertThat(e, is(instanceOf(type)));

		if (message != null) {
			assertThat(e.getRootCause(), is(notNullValue()));
			assertThat(e.getRootCause().getMessage(), containsString(message));
		}
	}
}
