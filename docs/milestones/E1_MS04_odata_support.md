## 🚀 Milestone: Full OASIS OData V4 support
 🌌 Road to Version 1.0

### 📝 Milestone Description

The OData Endpoint NeonBee provides an interface exposing fully-typed and structured entity information via the standardized [OASIS OData V4 protocol](https://www.oasis-open.org/committees/tc_home.php?wg_abbrev=odata), an industry standard driven by Microsoft, SAP, Dell and more. The open source Olingo library, the standard Java implementation for OData V4, is being used in NeonBees OData Endpoint to provide a protocol compliant HTTP endpoint.

However Olingo merely provides the frame (so in some sense it's a true to the word "frame"-work) for a proper OData V4 compliant interface implementation and essentially all parts of the framework, primarily consist of interfaces / abstract classes to provide a actual implementation for. This leads to a high effort implementing all parts of the standard. Parts like complex filter queries, navigation properties, expands, counts, etc. essentially all need a concrete implementation in OData, which makes it hard to cover everything from the get go.

What complicates the implementation further, is that Olingo by design, was not focused on an asynchronous implementation in the first place. What that means is that for many parts of the framework, workarounds had to be found, in order to call certain parts of the framework in an asynchronous manner, without simply plugging the whole framework in a big `executeBlocking`, which would have come with performance degradation for one of the main NeonBee interface components. Thus implementation of further interfaces for a improved standard compliance are sometimes harder to achieve, than simply copying the reference implementation from the Olingo implementation guide.

This essentially leads to, that in NeonBees current implementation of the protocol have to be considered incomplete. Without a real analysis, about 50-70% might be implemented, but important parts, such as navigation properties or complex filter / count support are plain missing. Due to the "workarounds" which had to be found in order to make the current framework implementation asynchronous, bugs could still be present in areas of code with very low to no code coverage, such as the batch implementation.

This milestone item is about providing an OData V4 endpoint, which is to 100% compliant to the OASIS standard, including all features, such as a proper implementation of batch processing, navigation properties etc. To achieve this, Olingo or the switch to another standardized library could be considered. Unfortunately, for Java there are no real alternatives to Olingo. The only major and up-to-date standard compliant framework is the Node.js based framework CDS. The usage of GraalVM and [es4x](https://reactiverse.io/es4x/), might be considered. As an alternative Olingo needs to be fully implemented, maybe more workarounds or framework extensions (for a unfortunately next to unmaintained framework) need to be found and properly tested for asynchronous use. Another idea is to split the endpoint implementations in a CDSODataEndpoint and a OlingoODataEndpoint and have users of NeonBee chose, which OData V4 compliant endpoint implementation they prefer: a fully standardized, but GraalVM dependent, CDS based endpoint, or a natively implemented, but maybe not 100% standard compliant Olingo based endpoint.

As a part of this item, an analysis of the current endpoint implementation is required to determine the degree of complying to the standard.

## Tasks / Features

- Consider splitting the existing OData endpoint into an Olingo and a CDS based OData implementation
- Analyze the current endpoint for how much it complies to the OASIS standard already
- Provide at least one endpoint as close as possible to 100% standard compliant to the OASIS OData V4 specification
- Increase test coverage of the existing endpoint and full test coverage for any new implementation