/*
 * Copyright 2017-2020 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;

import lombok.Data;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.convert.LazyLoadingTestUtils;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import com.mongodb.MongoClient;

/**
 * {@link org.springframework.data.mongodb.core.mapping.DBRef} related integration tests for
 * {@link org.springframework.data.mongodb.core.MongoTemplate}.
 *
 * @author Christoph Strobl
 */
public class MongoTemplateDbRefTests {

	MongoTemplate template;
	MongoTemplate otherDbTemplate;

	@Before
	public void setUp() {

		template = new MongoTemplate(new MongoClient(), "mongo-template-dbref-tests");

		template.dropCollection(RefCycleLoadingIntoDifferentTypeRoot.class);
		template.dropCollection(RefCycleLoadingIntoDifferentTypeIntermediate.class);
		template.dropCollection(RefCycleLoadingIntoDifferentTypeRootView.class);
		template.dropCollection(WithRefToAnotherDb.class);
		template.dropCollection(WithLazyRefToAnotherDb.class);
		template.dropCollection(WithListRefToAnotherDb.class);
		template.dropCollection(WithLazyListRefToAnotherDb.class);

		otherDbTemplate = new MongoTemplate(new MongoClient(), "mongo-template-dbref-tests-other-db");
		otherDbTemplate.dropCollection(JustSomeType.class);
	}

	@Test // DATAMONGO-1703
	public void shouldLoadRefIntoDifferentTypeCorrectly() {

		// init root
		RefCycleLoadingIntoDifferentTypeRoot root = new RefCycleLoadingIntoDifferentTypeRoot();
		root.id = "root-1";
		root.content = "jon snow";
		template.save(root);

		// init one and set view id ref to root.id
		RefCycleLoadingIntoDifferentTypeIntermediate intermediate = new RefCycleLoadingIntoDifferentTypeIntermediate();
		intermediate.id = "one-1";
		intermediate.refToRootView = new RefCycleLoadingIntoDifferentTypeRootView();
		intermediate.refToRootView.id = root.id;

		template.save(intermediate);

		// add one ref to root
		root.refToIntermediate = intermediate;
		template.save(root);

		RefCycleLoadingIntoDifferentTypeRoot loaded = template.findOne(query(where("id").is(root.id)),
				RefCycleLoadingIntoDifferentTypeRoot.class);

		assertThat(loaded.content).isEqualTo("jon snow");
		assertThat(loaded.getRefToIntermediate()).isInstanceOf(RefCycleLoadingIntoDifferentTypeIntermediate.class);
		assertThat(loaded.getRefToIntermediate().getRefToRootView())
				.isInstanceOf(RefCycleLoadingIntoDifferentTypeRootView.class);
		assertThat(loaded.getRefToIntermediate().getRefToRootView().getContent()).isEqualTo("jon snow");
	}

	@Test // DATAMONGO-2223
	public void shouldResolveSingleDBRefToAnotherDb() {

		JustSomeType one = new JustSomeType();
		one.value = "one";

		otherDbTemplate.insert(one);

		WithRefToAnotherDb source = new WithRefToAnotherDb();
		source.value = one;

		template.save(source);

		WithRefToAnotherDb target = template.findOne(query(where("id").is(source.id)), WithRefToAnotherDb.class);
		assertThat(target.getValue()).isEqualTo(one);
	}

	@Test // DATAMONGO-2223
	public void shouldResolveSingleLazyDBRefToAnotherDb() {

		JustSomeType one = new JustSomeType();
		one.value = "one";

		otherDbTemplate.insert(one);

		WithLazyRefToAnotherDb source = new WithLazyRefToAnotherDb();
		source.value = one;

		template.save(source);

		WithLazyRefToAnotherDb target = template.findOne(query(where("id").is(source.id)), WithLazyRefToAnotherDb.class);
		LazyLoadingTestUtils.assertProxyIsResolved(target.value, false);
		assertThat(target.getValue()).isEqualTo(one);
	}

	@Test // DATAMONGO-2223
	public void shouldResolveListDBRefToAnotherDb() {

		JustSomeType one = new JustSomeType();
		one.value = "one";

		JustSomeType two = new JustSomeType();
		two.value = "two";

		otherDbTemplate.insertAll(Arrays.asList(one, two));

		WithListRefToAnotherDb source = new WithListRefToAnotherDb();
		source.value = Arrays.asList(one, two);

		template.save(source);

		WithListRefToAnotherDb target = template.findOne(query(where("id").is(source.id)), WithListRefToAnotherDb.class);
		assertThat(target.getValue()).containsExactlyInAnyOrder(one, two);
	}

	@Test // DATAMONGO-2223
	public void shouldResolveLazyListDBRefToAnotherDb() {

		JustSomeType one = new JustSomeType();
		one.value = "one";

		JustSomeType two = new JustSomeType();
		two.value = "two";

		otherDbTemplate.insertAll(Arrays.asList(one, two));

		WithLazyListRefToAnotherDb source = new WithLazyListRefToAnotherDb();
		source.value = Arrays.asList(one, two);

		template.save(source);

		WithLazyListRefToAnotherDb target = template.findOne(query(where("id").is(source.id)),
				WithLazyListRefToAnotherDb.class);
		LazyLoadingTestUtils.assertProxyIsResolved(target.value, false);
		assertThat(target.getValue()).containsExactlyInAnyOrder(one, two);
	}

	@Data
	@Document("cycle-with-different-type-root")
	static class RefCycleLoadingIntoDifferentTypeRoot {

		@Id String id;
		String content;
		@DBRef RefCycleLoadingIntoDifferentTypeIntermediate refToIntermediate;
	}

	@Data
	@Document("cycle-with-different-type-intermediate")
	static class RefCycleLoadingIntoDifferentTypeIntermediate {

		@Id String id;
		@DBRef RefCycleLoadingIntoDifferentTypeRootView refToRootView;
	}

	@Data
	@Document("cycle-with-different-type-root")
	static class RefCycleLoadingIntoDifferentTypeRootView {

		@Id String id;
		String content;
	}

	@Data
	static class WithRefToAnotherDb {

		@Id String id;
		@DBRef(db = "mongo-template-dbref-tests-other-db") JustSomeType value;
	}

	@Data
	static class WithLazyRefToAnotherDb {

		@Id String id;
		@DBRef(lazy = true, db = "mongo-template-dbref-tests-other-db") JustSomeType value;
	}

	@Data
	static class WithListRefToAnotherDb {

		@Id String id;
		@DBRef(db = "mongo-template-dbref-tests-other-db") List<JustSomeType> value;
	}

	@Data
	static class WithLazyListRefToAnotherDb {

		@Id String id;
		@DBRef(lazy = true, db = "mongo-template-dbref-tests-other-db") List<JustSomeType> value;
	}

	@Data
	static class JustSomeType {

		@Id String id;
		String value;
	}

}
