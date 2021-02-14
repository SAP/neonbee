## 🚀 Milestone: Documentation and homepage
 🌌 Road to Version 1.0

### 📝 Milestone Description

NeonBee is currently missing a Vert.x style documentation. While there are static code checks in place checking for a proper JavaDoc, no additional documentation is provided, explaining the high level architecture and / or the concepts in NeonBee. Neither the development model for e.g. Data- and EntityVerticles is explained, nor the configuration options for instance the ServerVerticle provides. This makes it very hard for end-users to know how to use NeonBee. Similarly the homepage is currently not providing any kind of information material. Everything, if at all, is provided in JavaDoc, or on code level.

This milestone item is about the creation of a proper, Vert.x style, documentation in ASCIIdoc. As a first step this will require a discussion about consistent naming for the different layers in NeonBee, which so far never have been put in a terminology / glossary of some sort. This e.g. applies to "endpoints", "entry verticles", etc. Afterwards the documentation should include a high level architecture diagram, a general description of "what is NeonBee" and a development guide for the different development areas in NeonBee, like DataVerticle, EntityVerticle, but also hooks, models and generally all parts of the public API. All of the documentation should be made available, easy to consume via our NeonBee.io homepage and should be similar to the Vert.x style of documentation.

Last but not least the homepage should be revised to feature high level information about NeonBee. The logo also needs another iteration, as so far te stock-image was only bought and slightly modified, but we wanted to use the glasses of the bee, to introduce some more neon colors, such as harlequin green.

## Tasks / Features

- Provide a documentation in a Vert.x style layout for all publicly exposed APIs, including examples and explanation of potential use cases and development patterns
- The documentation written in ASCIIdoc should be exposed to the homepage of NeonBee
- The documentation shall include a high level architecture overview and a description of all of NeonBees concepts and configuration options, to explain how to develop, use, configure, operate and extend NeonBee
- The homepage should be revised and feature high level information and a general description of "What is NeonBee"
- The logo should be brought into a second iteration, introducing some more neon colors, such as harlequin green