/*
 * Copyright 2014-2020 the original author or authors.
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
package org.springframework.data.mongodb.core.index;

import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver.IndexDefinitionHolder;
import org.springframework.data.util.TypeInformation;

/**
 * {@link IndexResolver} finds those {@link IndexDefinition}s to be created for a given class.
 *
 * @author Christoph Strobl
 * @author Thomas Darimont
 * @since 1.5
 */
interface IndexResolver {

	/**
	 * Find and create {@link IndexDefinition}s for properties of given {@link TypeInformation}. {@link IndexDefinition}s are created
	 * for properties and types with {@link Indexed}, {@link CompoundIndexes} or {@link GeoSpatialIndexed}.
	 *
	 * @param typeInformation
	 * @return Empty {@link Iterable} in case no {@link IndexDefinition} could be resolved for type.
	 */
	Iterable<? extends IndexDefinitionHolder> resolveIndexFor(TypeInformation<?> typeInformation);

}
