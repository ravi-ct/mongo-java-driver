/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.client.model.expressions;

import com.mongodb.client.model.Field;
import com.mongodb.client.model.OperationTest;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonReader;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.conversions.Bson;
import org.bson.json.JsonReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.mongodb.ClusterFixture.serverVersionAtLeast;
import static com.mongodb.client.model.Aggregates.addFields;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractExpressionsFunctionalTest extends OperationTest {

    @BeforeEach
    public void setUp() {
        getCollectionHelper().drop();
    }

    @AfterEach
    public void tearDown() {
        getCollectionHelper().drop();
    }

    protected void assertExpression(final Object expectedResult, final Expression expression, final String expectedMql) {
        assertEval(expectedResult, expression);

        BsonValue expressionValue = ((MqlExpression<?>) expression).toBsonValue(fromProviders(new BsonValueCodecProvider()));
        BsonValue bsonValue = new BsonDocumentFragmentCodec().readValue(
                new JsonReader(expectedMql),
                DecoderContext.builder().build());
        assertEquals(bsonValue, expressionValue, expressionValue.toString().replace("\"", "'"));
    }

    private void assertEval(final Object expected, final Expression toEvaluate) {
        BsonValue evaluated = evaluate(toEvaluate);
        assertEquals(new Document("val", expected).toBsonDocument().get("val"), evaluated);
    }

    protected BsonValue evaluate(final Expression toEvaluate) {
        Bson addFieldsStage = addFields(new Field<>("val", toEvaluate));
        List<Bson> stages = new ArrayList<>();
        stages.add(addFieldsStage);
        List<BsonDocument> results;
        if (getCollectionHelper().count() == 0) {
            BsonDocument document = new BsonDocument("val", new BsonString("#invalid string#"));
            if (serverVersionAtLeast(5, 1)) {
                Bson documentsStage = new BsonDocument("$documents", new BsonArray(Arrays.asList(document)));
                stages.add(0, documentsStage);
                results = getCollectionHelper().aggregateDb(stages);
            } else {
                getCollectionHelper().insertDocuments(document);
                results = getCollectionHelper().aggregate(stages);
                getCollectionHelper().drop();
            }
        } else {
            results = getCollectionHelper().aggregate(stages);
        }
        BsonValue evaluated = results.get(0).get("val");
        return evaluated;
    }

    private static class BsonDocumentFragmentCodec extends BsonDocumentCodec {
        public BsonValue readValue(final BsonReader reader, final DecoderContext decoderContext) {
            reader.readBsonType();
            return super.readValue(reader, decoderContext);
        }
    }
}
