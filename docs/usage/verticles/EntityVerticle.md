# EntityVerticle

# Create

A `POST` request will be used to create a new entity. In this case, the `Future<EntityWrapper> createData(DataQuery query, DataContext context)` method of the responsible `EntityVerticle` will be invoked.
According to the best-practices of REST-API, the response of such a POST-request should
* contain a `Location`-header with the location of the newly created entity
* return the entity representation in the response body

To achieve this, a `EntityVerticle` should in the `createEntity`-method
* set the `Location` in the response context data
```
context.responseData().put("Location", /io.neonbee.test3.TestService3/TestCars(/unique-id));
```
* return a filled `EntityWrapper`
```
return Future.succeededFuture(new EntityWrapper(TEST_ENTITY_SET_FQN, createEntity(ENTITY_DATA_1));
```
In this case, a 201 status code with the entity representation will be returned in the HTTP-response.
The response context data under the key `Location` will be set as HTTP-header as well.

Just for backward-compatibility, a 204 status code will be returned, if no entity is returned in the `EntityWrapper` by the verticle.

# Update

A `PUT` request will be used to update an existing entity. In this case, the `Future<EntityWrapper> updateData(DataQuery query, DataContext context)` method of the responsible `EntityVerticle` will be invoked.
According to the best-practices of REST-API, the response of such a PUT-request should return the entity representation in the response body

To achieve this, an `EntityVerticle` should return a filled `EntityWrapper` in the `updateEntity`-method
```
return Future.succeededFuture(new EntityWrapper(TEST_ENTITY_SET_FQN, createEntity(ENTITY_DATA_1));
```
In this case, a 200 status code with the entity representation will be returned in the HTTP-response.

Just for backward-compatibility, a 204 status code will be returned, if no entity is returned in the `EntityWrapper` by the verticle.
