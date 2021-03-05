## 🚀 Milestone: Improve inter-verticle entity exchange
 🌌 Road to Version 1.0

### 📝 Milestone Description

Currently there are two concept of data exchange in NeonBee:

1. Exchanging arbitrary (un- / or structured) data via DataVerticles, without NeonBee knowing about the data structures to exchange and
2. Exchange of fully-structured and qualified entities via EntityVerticles.

This exchange currently happens via the eventbus and using the `requestData` methods of the DataVerticle. There are multiple issues / inconveniences with the current implementation.

Currently Olingo is the basis for both the OData endpoint, it was the main structured description interpreter before we switched to a two-model approach using CDS and it is also the foundation of the `Entity` type used to exchange entities between EntityVerticles. Working with Olingos entities is very cumbersome, as the interface of the object isn't great and next to no simplifications like builders, POJOs or similar are provided. Meaning that creation of entities is a pain for creators of EntityVerticles, as they cannot simply work with POJOs and have to resort a weird typed interface of Olingo.

Also there was never a good concept for how EntityVerticles should receive the query of which object they should return. Currently the `UriInfo` parser of Olingo is used in EntityVerticle to parse this information out of the URL path component. However NeonBee does provide absolutely no support in constructing or in the interpretations of such queries, which often leads to EntityVerticles simply always returning all data, instead of really checking the query and the OData endpoint then performing a filtering / sorting operation as a final step in the processing, which is very inefficient, as it causes a very high traffic via the event bus.

For the serialization of structured `Entity` object to the event bus an own `EntityWrapper` object and a codec provided by NeonBee is used. It should be considered if there are more efficient ways of for example pre-encoding the entities at the time the properties are set to the entity, instead of doing this inefficiently and synchronous during the eventbus communication.

Last but not least, the decision of using Olingo throughout the entity part of NeonBee, we have a quite tight entanglement of a otherwise infrequently maintained library.

For these reasons, but also the current handling in NeonBee leads to a more common usage of DataVerticles over EntityVerticles, even though DataVerticles can only be coupled loosely and provide next to no structured interface between them, which makes communication very unsafe / untrustworthy, especially between multiple components in distributed landscapes.

This milestone should be used to strengthen the whole entity handling in NeonBee, loosen the entanglement to Olingo, by reducing it to the OData endpoint only, make DataVerticle to EntityVerticle and inter-EntityVerticle communication significantly easier by for example providing POJO generation or a (own?) simplified entity object with a modern builder pattern, which can be more efficiently encoded and exchanged via the eventbus. This in the longrun will hopefully lead to less developers using unchecked DataVerticle communication and rather use strongly-typed interfaces of EntityVerticles for exchanging data.

## Tasks / Features

- POJO / builder concept for dealing with entities
- Clear interface to exchange and parse entity queries (similar to DataQuery with an EntityQuery)
- Pass OData query options in an easy consumable fashion to the DataSource so that these DataSources only return required data and make network communication more efficient.
- Reduce the usage of Olingo to the OData endpoint
- Improved and more efficient Entity/EntityWrapper, specialized for NeonBee and the serialization to the event bus
- Stop sending Olingo entities via the event bus
- Own entity exchange format or something open source better / more frequently maintained than Olingo (maybe parts of Googles Protocol Buffers framework could be utilized)
- Simplified fully-typed exchange of data over pure (JSON-based) data verticle communication
- EntityQuery vs. a full EntityRequest (DataRequest(String, String, DataQuery) constructor and add DataRequest(String, String) signature are easy to be confused / unclear if requesting data with an namespace or an entity with a namespace)