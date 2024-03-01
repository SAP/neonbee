# NeonBee \<TITLE\> RFC \<NUMBER\>

### Contents
- [Abstract](#abstract)
- [Motivation](#Motivation)
- [Technical Details](#technical-details)
- [Performance Considerations](#performance-considerations)
- [Impact on Existing Functionalities](#impact-on-existing-functionalities)
- [Open Questions](#open-questions)


### Abstract

We encountered a problem with the current implementation of the EntityVerticle, which is causing "No verticle registered listening to entity type name" error messages. This RFC proposes a solution to this problem. The solution is necessary because it will improve the overall stability of the system.

### Motivation

Running NeonBee in a clustered mode with multiple instances, we encountered a problem with the current implementation of the EntityVerticle. The problem is that some EntityVerticle are not deployed successfully. This is causing "No verticle registered listening to entity type name" error messages. This is a problem because it is causing that dependent verticles ca not fulfill there purpose. The proposed solution is to change the implementation of the EntityVerticle to handle multiple instances of the same verticle.

### Technical Details

Describe the idea in appropriate detail, how it fits in the overall architecture and how it aligns with the product vision.
Document in detail how the implementation of this RFC will be carried out.
The size of this may vary between a few sentences to multiple standalone subsections, depending on the scope of the change/addition.

Feel free to include code samples, diagrams, and any further content which facilitates the visualization and/or comprehension of the RFC scope.

### Performance Considerations

As performance is one of our core values, performance considerations should be separately described, where applicable.
Any limitations, or expected significant performance degradations should be listed here.

### Impact on Existing Functionalities

Any impacts on existing functionality, breaking changes, or deviation from product vision should be documented here, alongside explanations why they cannot/should not be circumvented.

### Open Questions

Any open questions that the submitter is seeking feedback for should be listed here in as much detail as possible, if applicable.