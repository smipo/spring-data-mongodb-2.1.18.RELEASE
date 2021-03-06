/*
 * Copyright 2010-2020 the original author or authors.
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
package org.springframework.data.mongodb.core.query;

import static org.springframework.util.ObjectUtils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.BSON;
import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.bson.types.Binary;
import org.springframework.data.domain.Example;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Shape;
import org.springframework.data.mongodb.InvalidMongoDbApiUsageException;
import org.springframework.data.mongodb.core.geo.GeoJson;
import org.springframework.data.mongodb.core.geo.Sphere;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject.Type;
import org.springframework.data.mongodb.core.schema.JsonSchemaProperty;
import org.springframework.data.mongodb.core.schema.MongoJsonSchema;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.mongodb.BasicDBList;

/**
 * Central class for creating queries. It follows a fluent API style so that you can easily chain together multiple
 * criteria. Static import of the 'Criteria.where' method will improve readability.
 *
 * @author Thomas Risberg
 * @author Oliver Gierke
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Andreas Zink
 */
public class Criteria implements CriteriaDefinition {

	/**
	 * Custom "not-null" object as we have to be able to work with {@literal null} values as well.
	 */
	private static final Object NOT_SET = new Object();

	private @Nullable String key;
	private List<Criteria> criteriaChain;
	private LinkedHashMap<String, Object> criteria = new LinkedHashMap<String, Object>();
	private @Nullable Object isValue = NOT_SET;

	public Criteria() {
		this.criteriaChain = new ArrayList<Criteria>();
	}

	public Criteria(String key) {
		this.criteriaChain = new ArrayList<Criteria>();
		this.criteriaChain.add(this);
		this.key = key;
	}

	protected Criteria(List<Criteria> criteriaChain, String key) {
		this.criteriaChain = criteriaChain;
		this.criteriaChain.add(this);
		this.key = key;
	}

	/**
	 * Static factory method to create a Criteria using the provided key
	 *
	 * @param key
	 * @return
	 */
	public static Criteria where(String key) {
		return new Criteria(key);
	}

	/**
	 * Static factory method to create a {@link Criteria} matching an example object.
	 *
	 * @param example must not be {@literal null}.
	 * @return
	 * @see Criteria#alike(Example)
	 * @since 1.8
	 */
	public static Criteria byExample(Object example) {
		return byExample(Example.of(example));
	}

	/**
	 * Static factory method to create a {@link Criteria} matching an example object.
	 *
	 * @param example must not be {@literal null}.
	 * @return
	 * @see Criteria#alike(Example)
	 * @since 1.8
	 */
	public static Criteria byExample(Example<?> example) {
		return new Criteria().alike(example);
	}

	/**
	 * Static factory method to create a {@link Criteria} matching documents against a given structure defined by the
	 * {@link MongoJsonSchema} using ({@code $jsonSchema}) operator.
	 *
	 * @param schema must not be {@literal null}.
	 * @return this
	 * @since 2.1
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/jsonSchema/">MongoDB Query operator:
	 *      $jsonSchema</a>
	 */
	public static Criteria matchingDocumentStructure(MongoJsonSchema schema) {
		return new Criteria().andDocumentStructureMatches(schema);
	}

	/**
	 * Static factory method to create a Criteria using the provided key
	 *
	 * @return
	 */
	public Criteria and(String key) {
		return new Criteria(this.criteriaChain, key);
	}

	/**
	 * Creates a criterion using equality
	 *
	 * @param o
	 * @return
	 */
	public Criteria is(@Nullable Object o) {

		if (!isValue.equals(NOT_SET)) {
			throw new InvalidMongoDbApiUsageException(
					"Multiple 'is' values declared. You need to use 'and' with multiple criteria");
		}

		if (lastOperatorWasNot()) {
			throw new InvalidMongoDbApiUsageException("Invalid query: 'not' can't be used with 'is' - use 'ne' instead.");
		}

		this.isValue = o;
		return this;
	}

	private boolean lastOperatorWasNot() {
		return !this.criteria.isEmpty() && "$not".equals(this.criteria.keySet().toArray()[this.criteria.size() - 1]);
	}

	/**
	 * Creates a criterion using the {@literal $ne} operator.
	 *
	 * @param o
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/ne/">MongoDB Query operator: $ne</a>
	 */
	public Criteria ne(@Nullable Object o) {
		criteria.put("$ne", o);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $lt} operator.
	 *
	 * @param o
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/lt/">MongoDB Query operator: $lt</a>
	 */
	public Criteria lt(Object o) {
		criteria.put("$lt", o);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $lte} operator.
	 *
	 * @param o
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/lte/">MongoDB Query operator: $lte</a>
	 */
	public Criteria lte(Object o) {
		criteria.put("$lte", o);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $gt} operator.
	 *
	 * @param o
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/gt/">MongoDB Query operator: $gt</a>
	 */
	public Criteria gt(Object o) {
		criteria.put("$gt", o);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $gte} operator.
	 *
	 * @param o
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/gte/">MongoDB Query operator: $gte</a>
	 */
	public Criteria gte(Object o) {
		criteria.put("$gte", o);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $in} operator.
	 *
	 * @param o the values to match against
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/in/">MongoDB Query operator: $in</a>
	 */
	public Criteria in(Object... o) {
		if (o.length > 1 && o[1] instanceof Collection) {
			throw new InvalidMongoDbApiUsageException(
					"You can only pass in one argument of type " + o[1].getClass().getName());
		}
		criteria.put("$in", Arrays.asList(o));
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $in} operator.
	 *
	 * @param c the collection containing the values to match against
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/in/">MongoDB Query operator: $in</a>
	 */
	public Criteria in(Collection<?> c) {
		criteria.put("$in", c);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $nin} operator.
	 *
	 * @param o
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/nin/">MongoDB Query operator: $nin</a>
	 */
	public Criteria nin(Object... o) {
		return nin(Arrays.asList(o));
	}

	/**
	 * Creates a criterion using the {@literal $nin} operator.
	 *
	 * @param o
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/nin/">MongoDB Query operator: $nin</a>
	 */
	public Criteria nin(Collection<?> o) {
		criteria.put("$nin", o);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $mod} operator.
	 *
	 * @param value
	 * @param remainder
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/mod/">MongoDB Query operator: $mod</a>
	 */
	public Criteria mod(Number value, Number remainder) {
		List<Object> l = new ArrayList<Object>();
		l.add(value);
		l.add(remainder);
		criteria.put("$mod", l);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $all} operator.
	 *
	 * @param o
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/all/">MongoDB Query operator: $all</a>
	 */
	public Criteria all(Object... o) {
		return all(Arrays.asList(o));
	}

	/**
	 * Creates a criterion using the {@literal $all} operator.
	 *
	 * @param o
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/all/">MongoDB Query operator: $all</a>
	 */
	public Criteria all(Collection<?> o) {
		criteria.put("$all", o);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $size} operator.
	 *
	 * @param s
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/size/">MongoDB Query operator: $size</a>
	 */
	public Criteria size(int s) {
		criteria.put("$size", s);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $exists} operator.
	 *
	 * @param b
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/exists/">MongoDB Query operator: $exists</a>
	 */
	public Criteria exists(boolean b) {
		criteria.put("$exists", b);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $type} operator.
	 *
	 * @param t
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/type/">MongoDB Query operator: $type</a>
	 */
	public Criteria type(int t) {
		criteria.put("$type", t);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $type} operator.
	 *
	 * @param types must not be {@literal null}.
	 * @return this
	 * @since 2.1
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/type/">MongoDB Query operator: $type</a>
	 */
	public Criteria type(Type... types) {

		Assert.notNull(types, "Types must not be null!");
		Assert.noNullElements(types, "Types must not contain null.");

		criteria.put("$type", Arrays.asList(types).stream().map(Type::value).collect(Collectors.toList()));
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $not} meta operator which affects the clause directly following
	 *
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/not/">MongoDB Query operator: $not</a>
	 */
	public Criteria not() {
		return not(null);
	}

	/**
	 * Creates a criterion using the {@literal $not} operator.
	 *
	 * @param value
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/not/">MongoDB Query operator: $not</a>
	 */
	private Criteria not(@Nullable Object value) {
		criteria.put("$not", value);
		return this;
	}

	/**
	 * Creates a criterion using a {@literal $regex} operator.
	 *
	 * @param re
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/regex/">MongoDB Query operator: $regex</a>
	 */
	public Criteria regex(String re) {
		return regex(re, null);
	}

	/**
	 * Creates a criterion using a {@literal $regex} and {@literal $options} operator.
	 *
	 * @param re
	 * @param options
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/regex/">MongoDB Query operator: $regex</a>
	 */
	public Criteria regex(String re, @Nullable String options) {
		return regex(toPattern(re, options));
	}

	/**
	 * Syntactical sugar for {@link #is(Object)} making obvious that we create a regex predicate.
	 *
	 * @param pattern
	 * @return
	 */
	public Criteria regex(Pattern pattern) {

		Assert.notNull(pattern, "Pattern must not be null!");

		if (lastOperatorWasNot()) {
			return not(pattern);
		}

		this.isValue = pattern;
		return this;
	}

	public Criteria regex(BsonRegularExpression regex) {

		if (lastOperatorWasNot()) {
			return not(regex);
		}

		this.isValue = regex;
		return this;
	}

	private Pattern toPattern(String regex, @Nullable String options) {

		Assert.notNull(regex, "Regex string must not be null!");

		return Pattern.compile(regex, options == null ? 0 : BSON.regexFlags(options));
	}

	/**
	 * Creates a geospatial criterion using a {@literal $geoWithin $centerSphere} operation. This is only available for
	 * Mongo 2.4 and higher.
	 *
	 * @param circle must not be {@literal null}
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/geoWithin/">MongoDB Query operator:
	 *      $geoWithin</a>
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/centerSphere/">MongoDB Query operator:
	 *      $centerSphere</a>
	 */
	public Criteria withinSphere(Circle circle) {

		Assert.notNull(circle, "Circle must not be null!");

		criteria.put("$geoWithin", new GeoCommand(new Sphere(circle)));
		return this;
	}

	/**
	 * Creates a geospatial criterion using a {@literal $geoWithin} operation.
	 *
	 * @param shape
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/geoWithin/">MongoDB Query operator:
	 *      $geoWithin</a>
	 */
	public Criteria within(Shape shape) {

		Assert.notNull(shape, "Shape must not be null!");

		criteria.put("$geoWithin", new GeoCommand(shape));
		return this;
	}

	/**
	 * Creates a geospatial criterion using a {@literal $near} operation.
	 *
	 * @param point must not be {@literal null}
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/near/">MongoDB Query operator: $near</a>
	 */
	public Criteria near(Point point) {

		Assert.notNull(point, "Point must not be null!");

		criteria.put("$near", point);
		return this;
	}

	/**
	 * Creates a geospatial criterion using a {@literal $nearSphere} operation. This is only available for Mongo 1.7 and
	 * higher.
	 *
	 * @param point must not be {@literal null}
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/nearSphere/">MongoDB Query operator:
	 *      $nearSphere</a>
	 */
	public Criteria nearSphere(Point point) {

		Assert.notNull(point, "Point must not be null!");

		criteria.put("$nearSphere", point);
		return this;
	}

	/**
	 * Creates criterion using {@code $geoIntersects} operator which matches intersections of the given {@code geoJson}
	 * structure and the documents one. Requires MongoDB 2.4 or better.
	 *
	 * @param geoJson must not be {@literal null}.
	 * @return
	 * @since 1.8
	 */
	@SuppressWarnings("rawtypes")
	public Criteria intersects(GeoJson geoJson) {

		Assert.notNull(geoJson, "GeoJson must not be null!");
		criteria.put("$geoIntersects", geoJson);
		return this;
	}

	/**
	 * Creates a geo-spatial criterion using a {@literal $maxDistance} operation, for use with $near
	 *
	 * @param maxDistance
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/maxDistance/">MongoDB Query operator:
	 *      $maxDistance</a>
	 */
	public Criteria maxDistance(double maxDistance) {

		if (createNearCriteriaForCommand("$near", "$maxDistance", maxDistance)
				|| createNearCriteriaForCommand("$nearSphere", "$maxDistance", maxDistance)) {
			return this;
		}

		criteria.put("$maxDistance", maxDistance);
		return this;
	}

	/**
	 * Creates a geospatial criterion using a {@literal $minDistance} operation, for use with {@literal $near} or
	 * {@literal $nearSphere}.
	 *
	 * @param minDistance
	 * @return
	 * @since 1.7
	 */
	public Criteria minDistance(double minDistance) {

		if (createNearCriteriaForCommand("$near", "$minDistance", minDistance)
				|| createNearCriteriaForCommand("$nearSphere", "$minDistance", minDistance)) {
			return this;
		}

		criteria.put("$minDistance", minDistance);
		return this;
	}

	/**
	 * Creates a criterion using the {@literal $elemMatch} operator
	 *
	 * @param c
	 * @return
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/elemMatch/">MongoDB Query operator:
	 *      $elemMatch</a>
	 */
	public Criteria elemMatch(Criteria c) {
		criteria.put("$elemMatch", c.getCriteriaObject());
		return this;
	}

	/**
	 * Creates a criterion using the given object as a pattern.
	 *
	 * @param sample
	 * @return
	 * @since 1.8
	 */
	public Criteria alike(Example<?> sample) {

		criteria.put("$example", sample);
		return this;
	}

	/**
	 * Creates a criterion ({@code $jsonSchema}) matching documents against a given structure defined by the
	 * {@link MongoJsonSchema}. <br />
	 * <strong>NOTE:</strong> {@code $jsonSchema} cannot be used on field/property level but defines the whole document
	 * structure. Please use
	 * {@link org.springframework.data.mongodb.core.schema.MongoJsonSchema.MongoJsonSchemaBuilder#properties(JsonSchemaProperty...)}
	 * to specify nested fields or query them using the {@link #type(Type...) $type} operator.
	 *
	 * @param schema must not be {@literal null}.
	 * @return this
	 * @since 2.1
	 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/jsonSchema/">MongoDB Query operator:
	 *      $jsonSchema</a>
	 */
	public Criteria andDocumentStructureMatches(MongoJsonSchema schema) {

		Assert.notNull(schema, "Schema must not be null!");

		Criteria schemaCriteria = new Criteria();
		schemaCriteria.criteria.putAll(schema.toDocument());

		return registerCriteriaChainElement(schemaCriteria);
	}

	/**
	 * Use {@link BitwiseCriteriaOperators} as gateway to create a criterion using one of the
	 * <a href="https://docs.mongodb.com/manual/reference/operator/query-bitwise/">bitwise operators</a> like
	 * {@code $bitsAllClear}.
	 *
	 * @return new instance of {@link BitwiseCriteriaOperators}. Never {@literal null}.
	 * @since 2.1
	 */
	public BitwiseCriteriaOperators bits() {
		return new BitwiseCriteriaOperatorsImpl(this);
	}

	/**
	 * Creates an 'or' criteria using the $or operator for all of the provided criteria
	 * <p>
	 * Note that mongodb doesn't support an $or operator to be wrapped in a $not operator.
	 * <p>
	 *
	 * @throws IllegalArgumentException if {@link #orOperator(Criteria...)} follows a not() call directly.
	 * @param criteria
	 */
	public Criteria orOperator(Criteria... criteria) {
		BasicDBList bsonList = createCriteriaList(criteria);
		return registerCriteriaChainElement(new Criteria("$or").is(bsonList));
	}

	/**
	 * Creates a 'nor' criteria using the $nor operator for all of the provided criteria.
	 * <p>
	 * Note that mongodb doesn't support an $nor operator to be wrapped in a $not operator.
	 * <p>
	 *
	 * @throws IllegalArgumentException if {@link #norOperator(Criteria...)} follows a not() call directly.
	 * @param criteria
	 */
	public Criteria norOperator(Criteria... criteria) {
		BasicDBList bsonList = createCriteriaList(criteria);
		return registerCriteriaChainElement(new Criteria("$nor").is(bsonList));
	}

	/**
	 * Creates an 'and' criteria using the $and operator for all of the provided criteria.
	 * <p>
	 * Note that mongodb doesn't support an $and operator to be wrapped in a $not operator.
	 * <p>
	 *
	 * @throws IllegalArgumentException if {@link #andOperator(Criteria...)} follows a not() call directly.
	 * @param criteria
	 */
	public Criteria andOperator(Criteria... criteria) {
		BasicDBList bsonList = createCriteriaList(criteria);
		return registerCriteriaChainElement(new Criteria("$and").is(bsonList));
	}

	private Criteria registerCriteriaChainElement(Criteria criteria) {

		if (lastOperatorWasNot()) {
			throw new IllegalArgumentException(
					"operator $not is not allowed around criteria chain element: " + criteria.getCriteriaObject());
		} else {
			criteriaChain.add(criteria);
		}
		return this;
	}

	/*
	 * @see org.springframework.data.mongodb.core.query.CriteriaDefinition#getKey()
	 */
	@Override
	@Nullable
	public String getKey() {
		return this.key;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.query.CriteriaDefinition#getCriteriaObject()
	 */
	public Document getCriteriaObject() {

		if (this.criteriaChain.size() == 1) {
			return criteriaChain.get(0).getSingleCriteriaObject();
		} else if (CollectionUtils.isEmpty(this.criteriaChain) && !CollectionUtils.isEmpty(this.criteria)) {
			return getSingleCriteriaObject();
		} else {
			Document criteriaObject = new Document();
			for (Criteria c : this.criteriaChain) {
				Document document = c.getSingleCriteriaObject();
				for (String k : document.keySet()) {
					setValue(criteriaObject, k, document.get(k));
				}
			}
			return criteriaObject;
		}
	}

	protected Document getSingleCriteriaObject() {

		Document document = new Document();
		boolean not = false;

		for (Entry<String, Object> entry : criteria.entrySet()) {

			String key = entry.getKey();
			Object value = entry.getValue();

			if (requiresGeoJsonFormat(value)) {
				value = new Document("$geometry", value);
			}

			if (not) {
				Document notDocument = new Document();
				notDocument.put(key, value);
				document.put("$not", notDocument);
				not = false;
			} else {
				if ("$not".equals(key) && value == null) {
					not = true;
				} else {
					document.put(key, value);
				}
			}
		}

		if (!StringUtils.hasText(this.key)) {
			if (not) {
				return new Document("$not", document);
			}
			return document;
		}

		Document queryCriteria = new Document();

		if (!NOT_SET.equals(isValue)) {
			queryCriteria.put(this.key, this.isValue);
			queryCriteria.putAll(document);
		} else {
			queryCriteria.put(this.key, document);
		}

		return queryCriteria;
	}

	private BasicDBList createCriteriaList(Criteria[] criteria) {
		BasicDBList bsonList = new BasicDBList();
		for (Criteria c : criteria) {
			bsonList.add(c.getCriteriaObject());
		}
		return bsonList;
	}

	private void setValue(Document document, String key, Object value) {
		Object existing = document.get(key);
		if (existing == null) {
			document.put(key, value);
		} else {
			throw new InvalidMongoDbApiUsageException("Due to limitations of the com.mongodb.BasicDocument, "
					+ "you can't add a second '" + key + "' expression specified as '" + key + " : " + value + "'. "
					+ "Criteria already contains '" + key + " : " + existing + "'.");
		}
	}

	private boolean createNearCriteriaForCommand(String command, String operation, double maxDistance) {

		if (!criteria.containsKey(command)) {
			return false;
		}

		Object existingNearOperationValue = criteria.get(command);

		if (existingNearOperationValue instanceof Document) {

			((Document) existingNearOperationValue).put(operation, maxDistance);

			return true;

		} else if (existingNearOperationValue instanceof GeoJson) {

			Document dbo = new Document("$geometry", existingNearOperationValue).append(operation, maxDistance);
			criteria.put(command, dbo);

			return true;
		}

		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (obj == null || !getClass().equals(obj.getClass())) {
			return false;
		}

		Criteria that = (Criteria) obj;

		if (this.criteriaChain.size() != that.criteriaChain.size()) {
			return false;
		}

		for (int i = 0; i < this.criteriaChain.size(); i++) {

			Criteria left = this.criteriaChain.get(i);
			Criteria right = that.criteriaChain.get(i);

			if (!simpleCriteriaEquals(left, right)) {
				return false;
			}
		}

		return true;
	}

	private boolean simpleCriteriaEquals(Criteria left, Criteria right) {

		boolean keyEqual = left.key == null ? right.key == null : left.key.equals(right.key);
		boolean criteriaEqual = left.criteria.equals(right.criteria);
		boolean valueEqual = isEqual(left.isValue, right.isValue);

		return keyEqual && criteriaEqual && valueEqual;
	}

	/**
	 * Checks the given objects for equality. Handles {@link Pattern} and arrays correctly.
	 *
	 * @param left
	 * @param right
	 * @return
	 */
	private boolean isEqual(Object left, Object right) {

		if (left == null) {
			return right == null;
		}

		if (Pattern.class.isInstance(left)) {

			if (!Pattern.class.isInstance(right)) {
				return false;
			}

			Pattern leftPattern = (Pattern) left;
			Pattern rightPattern = (Pattern) right;

			return leftPattern.pattern().equals(rightPattern.pattern()) //
					&& leftPattern.flags() == rightPattern.flags();
		}

		return ObjectUtils.nullSafeEquals(left, right);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {

		int result = 17;

		result += nullSafeHashCode(key);
		result += criteria.hashCode();
		result += nullSafeHashCode(isValue);

		return result;
	}

	private static boolean requiresGeoJsonFormat(Object value) {
		return value instanceof GeoJson
				|| (value instanceof GeoCommand && ((GeoCommand) value).getShape() instanceof GeoJson);
	}

	/**
	 * MongoDB specific <a href="https://docs.mongodb.com/manual/reference/operator/query-bitwise/">bitwise query
	 * operators</a> like {@code $bitsAllClear, $bitsAllSet,...} for usage with {@link Criteria#bits()} and {@link Query}.
	 *
	 * @author Christoph Strobl
	 * @since 2.1
	 * @see <a href=
	 *      "https://docs.mongodb.com/manual/reference/operator/query-bitwise/">https://docs.mongodb.com/manual/reference/operator/query-bitwise/</a>
	 * @currentRead Beyond the Shadows - Brent Weeks
	 */
	public interface BitwiseCriteriaOperators {

		/**
		 * Creates a criterion using {@literal $bitsAllClear} matching documents where all given bit positions are clear
		 * (i.e. 0).
		 *
		 * @param numericBitmask non-negative numeric bitmask.
		 * @return target {@link Criteria}.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAllClear/">MongoDB Query operator:
		 *      $bitsAllClear</a>
		 * @since 2.1
		 */
		Criteria allClear(int numericBitmask);

		/**
		 * Creates a criterion using {@literal $bitsAllClear} matching documents where all given bit positions are clear
		 * (i.e. 0).
		 *
		 * @param bitmask string representation of a bitmask that will be converted to its base64 encoded {@link Binary}
		 *          representation. Must not be {@literal null} nor empty.
		 * @return target {@link Criteria}.
		 * @throws IllegalArgumentException when bitmask is {@literal null} or empty.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAllClear/">MongoDB Query operator:
		 *      $bitsAllClear</a>
		 * @since 2.1
		 */
		Criteria allClear(String bitmask);

		/**
		 * Creates a criterion using {@literal $bitsAllClear} matching documents where all given bit positions are clear
		 * (i.e. 0).
		 *
		 * @param positions list of non-negative integer positions. Positions start at 0 from the least significant bit.
		 *          Must not be {@literal null} nor contain {@literal null} elements.
		 * @return target {@link Criteria}.
		 * @throws IllegalArgumentException when positions is {@literal null} or contains {@literal null} elements.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAllClear/">MongoDB Query operator:
		 *      $bitsAllClear</a>
		 * @since 2.1
		 */
		Criteria allClear(List<Integer> positions);

		/**
		 * Creates a criterion using {@literal $bitsAllSet} matching documents where all given bit positions are set (i.e.
		 * 1).
		 *
		 * @param numericBitmask non-negative numeric bitmask.
		 * @return target {@link Criteria}.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAllSet/">MongoDB Query operator:
		 *      $bitsAllSet</a>
		 * @since 2.1
		 */
		Criteria allSet(int numericBitmask);

		/**
		 * Creates a criterion using {@literal $bitsAllSet} matching documents where all given bit positions are set (i.e.
		 * 1).
		 *
		 * @param bitmask string representation of a bitmask that will be converted to its base64 encoded {@link Binary}
		 *          representation. Must not be {@literal null} nor empty.
		 * @return target {@link Criteria}.
		 * @throws IllegalArgumentException when bitmask is {@literal null} or empty.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAllSet/">MongoDB Query operator:
		 *      $bitsAllSet</a>
		 * @since 2.1
		 */
		Criteria allSet(String bitmask);

		/**
		 * Creates a criterion using {@literal $bitsAllSet} matching documents where all given bit positions are set (i.e.
		 * 1).
		 *
		 * @param positions list of non-negative integer positions. Positions start at 0 from the least significant bit.
		 *          Must not be {@literal null} nor contain {@literal null} elements.
		 * @return target {@link Criteria}.
		 * @throws IllegalArgumentException when positions is {@literal null} or contains {@literal null} elements.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAllSet/">MongoDB Query operator:
		 *      $bitsAllSet</a>
		 * @since 2.1
		 */
		Criteria allSet(List<Integer> positions);

		/**
		 * Creates a criterion using {@literal $bitsAllClear} matching documents where any given bit positions are clear
		 * (i.e. 0).
		 *
		 * @param numericBitmask non-negative numeric bitmask.
		 * @return target {@link Criteria}.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAnyClear/">MongoDB Query operator:
		 *      $bitsAnyClear</a>
		 * @since 2.1
		 */
		Criteria anyClear(int numericBitmask);

		/**
		 * Creates a criterion using {@literal $bitsAllClear} matching documents where any given bit positions are clear
		 * (i.e. 0).
		 *
		 * @param bitmask string representation of a bitmask that will be converted to its base64 encoded {@link Binary}
		 *          representation. Must not be {@literal null} nor empty.
		 * @return target {@link Criteria}.
		 * @throws IllegalArgumentException when bitmask is {@literal null} or empty.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAnyClear/">MongoDB Query operator:
		 *      $bitsAnyClear</a>
		 * @since 2.1
		 */
		Criteria anyClear(String bitmask);

		/**
		 * Creates a criterion using {@literal $bitsAllClear} matching documents where any given bit positions are clear
		 * (i.e. 0).
		 *
		 * @param positions list of non-negative integer positions. Positions start at 0 from the least significant bit.
		 *          Must not be {@literal null} nor contain {@literal null} elements.
		 * @return target {@link Criteria}.
		 * @throws IllegalArgumentException when positions is {@literal null} or contains {@literal null} elements.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAnyClear/">MongoDB Query operator:
		 *      $bitsAnyClear</a>
		 * @since 2.1
		 */
		Criteria anyClear(List<Integer> positions);

		/**
		 * Creates a criterion using {@literal $bitsAllSet} matching documents where any given bit positions are set (i.e.
		 * 1).
		 *
		 * @param numericBitmask non-negative numeric bitmask.
		 * @return target {@link Criteria}.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAnySet/">MongoDB Query operator:
		 *      $bitsAnySet</a>
		 * @since 2.1
		 */
		Criteria anySet(int numericBitmask);

		/**
		 * Creates a criterion using {@literal $bitsAnySet} matching documents where any given bit positions are set (i.e.
		 * 1).
		 *
		 * @param bitmask string representation of a bitmask that will be converted to its base64 encoded {@link Binary}
		 *          representation. Must not be {@literal null} nor empty.
		 * @return target {@link Criteria}.
		 * @throws IllegalArgumentException when bitmask is {@literal null} or empty.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAnySet/">MongoDB Query operator:
		 *      $bitsAnySet</a>
		 * @since 2.1
		 */
		Criteria anySet(String bitmask);

		/**
		 * Creates a criterion using {@literal $bitsAnySet} matching documents where any given bit positions are set (i.e.
		 * 1).
		 *
		 * @param positions list of non-negative integer positions. Positions start at 0 from the least significant bit.
		 *          Must not be {@literal null} nor contain {@literal null} elements.
		 * @return target {@link Criteria}.
		 * @throws IllegalArgumentException when positions is {@literal null} or contains {@literal null} elements.
		 * @see <a href="https://docs.mongodb.com/manual/reference/operator/query/bitsAnySet/">MongoDB Query operator:
		 *      $bitsAnySet</a>
		 * @since 2.1
		 */
		Criteria anySet(List<Integer> positions);

	}

	/**
	 * Default implementation of {@link BitwiseCriteriaOperators}.
	 * 
	 * @author Christoph Strobl
	 * @currentRead Beyond the Shadows - Brent Weeks
	 */
	private static class BitwiseCriteriaOperatorsImpl implements BitwiseCriteriaOperators {

		private final Criteria target;

		BitwiseCriteriaOperatorsImpl(Criteria target) {
			this.target = target;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#allClear(int)
		 */
		@Override
		public Criteria allClear(int numericBitmask) {
			return numericBitmask("$bitsAllClear", numericBitmask);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#allClear(java.lang.String)
		 */
		@Override
		public Criteria allClear(String bitmask) {
			return stringBitmask("$bitsAllClear", bitmask);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#allClear(java.util.List)
		 */
		@Override
		public Criteria allClear(List<Integer> positions) {
			return positions("$bitsAllClear", positions);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#allSet(int)
		 */
		@Override
		public Criteria allSet(int numericBitmask) {
			return numericBitmask("$bitsAllSet", numericBitmask);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#allSet(java.lang.String)
		 */
		@Override
		public Criteria allSet(String bitmask) {
			return stringBitmask("$bitsAllSet", bitmask);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#allSet(java.util.List)
		 */
		@Override
		public Criteria allSet(List<Integer> positions) {
			return positions("$bitsAllSet", positions);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#anyClear(int)
		 */
		@Override
		public Criteria anyClear(int numericBitmask) {
			return numericBitmask("$bitsAnyClear", numericBitmask);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#anyClear(java.lang.String)
		 */
		@Override
		public Criteria anyClear(String bitmask) {
			return stringBitmask("$bitsAnyClear", bitmask);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#anyClear(java.util.List)
		 */
		@Override
		public Criteria anyClear(List<Integer> positions) {
			return positions("$bitsAnyClear", positions);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#anySet(int)
		 */
		@Override
		public Criteria anySet(int numericBitmask) {
			return numericBitmask("$bitsAnySet", numericBitmask);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#anySet(java.lang.String)
		 */
		@Override
		public Criteria anySet(String bitmask) {
			return stringBitmask("$bitsAnySet", bitmask);
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.core.query.BitwiseCriteriaOperators#anySet(java.util.Collection)
		 */
		@Override
		public Criteria anySet(List<Integer> positions) {
			return positions("$bitsAnySet", positions);
		}

		private Criteria positions(String operator, List<Integer> positions) {

			Assert.notNull(positions, "Positions must not be null!");
			Assert.noNullElements(positions.toArray(), "Positions must not contain null values.");

			target.criteria.put(operator, positions);
			return target;
		}

		private Criteria stringBitmask(String operator, String bitmask) {

			Assert.hasText(bitmask, "Bitmask must not be null!");

			target.criteria.put(operator, new Binary(Base64Utils.decodeFromString(bitmask)));
			return target;
		}

		private Criteria numericBitmask(String operator, int bitmask) {

			target.criteria.put(operator, bitmask);
			return target;
		}
	}
}
