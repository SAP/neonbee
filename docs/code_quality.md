# Code Quality

## Content

- [Code Quality](#Code-Quality)
  - [Content](#Content)
  - [Purpose](#Purpose)
  - [Conventions](#Conventions)
    - [Test Coverage](#Test-Coverage)
    - [Architecture / Code Structure](#Architecture--Code-Structure)
    - [Documentation](#Documentation)
    - [Code Smells](#Code-Smells)
    - [Code Style](#Code-Style)
      - [Manual Checks](#Manual-Checks)
  - [Conventions Justification](#Conventions-Justification)
    - [Avoid final for local variables](#Avoid-final-for-local-variables)

## Purpose

Code bases with a high quality have a lot of benefits. High code quality helps to ...

- ... understand code easier.
- ... develop new features faster.
- ... increase the stability.
- ... attract new developers.

Code quality, or quality in the broader sense, has various manifestations. It is not easy to judge the quality of a project, but there are some indicators that may help. Some of these indicators are:

- test coverage
- architecture / code structure
- documentation
- code smells
- consistent code style
- idiomatic

In order to benefit from the points mentioned above and **to ensure** that NeonBee will also have a high level of **quality** in the future the **NeonBee community** has **established** some **conventions**.

Any **contribution** to NeonBee **must respect** and comply with **these conventions**.

## Conventions

**Following conventions is** often an **effort**, because if people did certain things directly, there would be no need for conventions. But in the NeonBee community **we believe** that **it's worth it**. To keep the additional effort as low as possible, we are trying to establish an automated check for every convention.

### Test Coverage

The targeted test coverage in NeonBee is 90% [line coverage](https://www.eclemma.org/jacoco/trunk/doc/counters.html). Java Code Coverage (JaCoCo) is already integrated into the gradle build script. At the moment the current code coverage is below 90%, which makes it hard to detect if a new change fulfils the 90% goal. As long as we have less then 90% code coverage at all, this requirement has to be checked manually.

> Rule: Any contribution **SHOULD** have a line coverage of 90%.

### Architecture / Code Structure

Especially more complex changes or features should be discussed in the NeonBee community to find the best possible implementation and architecture. Therefore the NeonBee community established an RFC process (TODO: link to RFC process) to request feedback from the community.

It is hard to define, when a contribution or feature requires a RFC. If possible this question can be clarified upfront. If this is not possible and the work on the contribution has already started, the NeonBee committers are allowed to request the start of an RFC process at this time as well, if the contribution turns out to be more complex than expected. This could theoretically result in a different solution that would make the effort already spent obsolete.

> Rule: Any contribution **MAY** need to start the RFC process upfront, but it can happen that it will be required later during the contribution.

### Documentation

A good documentation is essential for an open source project. But good documentation doesn't mean write for everything a comment / JavaDoc. These are examples for areas where a good documentation brings a highly valuable:

- Classes or functions that are part of a public API which is intended to be used by others.
- Configuration properties of classes or functions.
- Code that is hard to read but can't be changed due to performance reasons.
- ...

> Rule: Public methods or variables **MUST** have a JavaDoc.
>
> Rule: Public classes **SHOULD** have a JavaDoc.
>
> Rule: Private classes, methods or variables **MAY** have a JavaDoc.
>
> Rule: Think twice before writing an inline comment, maybe it can become obsolete by writing clean code.
>
> Rule: If code with comments changes, the comments **MUST** also be updated if they would become invalid afterwards.

For the simplest possible getters and setters it is allowed to write a simplified JavaDoc without a summary.

```java

    /**
     * @return the NeonBee configuration
     */
    public NeonBeeConfig getConfig() {
        return config;
    }

     /**
     * @param config The NeonBee config to set.
     */
    public NeonBeeConfig setConfig(NeonBeeConfig config) {
        this.config = config;
    }
```

If the getter or setter is more complex than in this examples, a more advanced JavaDoc **SHOULD** be written.

### Code Smells

The following static code checker are already integrated into our gradle build scripts:

- PMD
- SpotBugs
- Error Prone
- Spotless
- Checkstyle

and its planned to add more if it makes sense and is necessary. With the help of these static code checkers all code smells considered as relevant by the NeonBee community can be detected automatically.

> Rule: Any contribution **MUST NOT** increase the number of violations found by the static code checkers.
>
> Rule: It is allowed to suppress warnings, but then it must be justified and accepted by the community.

### Code Style

Code style has often two dimensions. The first dimension is measurable and includes improvements with regards to:

- code readability
- code maintenance
- code consistency
- ...

The other dimension is the personal affinity of each individual developer to the code and thus is very subjective. This dimension is very important because every developer is an artist with the motivation to create an own personal work of art. But precisely because of the subjectivity and personal affinity to the code, this dimension has the greatest potential for conflict if consistency is to be preserved.

To avoid intensive and emotional discussion about code style, the NeonBee community has defined conventions within all developers can realize.

With the help of static code checkers it is possible to enforce almost all conventions automatically. But there are some conventions which were not yet transformed into static code checks and must be checked manually.

> Rule: Any contribution **MUST NOT** increase the number of violations found by the static code checkers.
>
> Rule: Also manual checks **MUST** be performed.

**Note:** There are some obvious and implicit conventions like:

- don't declare fields at the bottom of a class
- don't use uppercase package names
- ...

These conventions might not be mentioned here, because most of the Java developers should know them by heart. But in case it becomes necessary due to a conflict during a review, we will add them here. If in doubt about how to style the code, use the Google [code style](https://google.github.io/styleguide/javaguide.html) as a guide.

#### Manual Checks

**Evaluating** conventions **manually** is a **lot of effort**, therefore **this section** should be **very short**.

- **(Code) Constants:** If a static value is used several times or externally, then this **SHOULD** be defined as a constant.
- **(Docs) Punctuation:** The first line of a method or class comment must be concluded with a '.'. In the documentation of @return, @param, @throws, a single sentence/phrase/expression must not be concluded with a '.', unless the documentation contains more than one sentence. In this case, comma and dot must be used.
- **(Docs) Capitalization:** First line of each comment must start with a capitalized letter. Documentation of @return, @param, @throws must start with uncapitalized letter.

## Conventions Justification

Some developers may be confused about why the NeonBee community has defined some conventions that are very unique. But of course, there is a well thought idea behind every convention. Some of these ideas are obvious, some are not. At this place we explain the less obvious ideas, so that they must not explained to everyone individually.

### Avoid final for local variables

In our opinion the only advantage of declaring local variables as final arises, when the type is primitive or immutable. In all other situations, the final modifier is worthless.

It is also not required to be thread safe, because Vert.x is handling this. It is not necessary to use variables in lambda expressions, because with Java 8+ these variables implicitly become final.

But adding final to declarations comes with disadvantages:

- More effort when overriding is desired.
- Increase of complexity, declarations should be short, simple and clean.
