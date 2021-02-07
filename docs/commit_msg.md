# Commit Message

## Content

- [Commit Message](#Commit-Message)
  - [Content](#Content)
  - [Purpose](#Purpose)
  - [Conventional Commits](#Conventional-Commits)
  - [Structure](#Structure)
    - [Trivial Changes](#Trivial-Changes)
  - [Best Practices](#Best-Practices)
    - [Summary](#Summary)
    - [Explanatory Text](#Explanatory-Text)
  - [Examples](#Examples)

## Purpose

A well written commit message has a lot of benefits. It can help ...

- ... the reviewer to understand the change and make a review more comfortable.
- ... to provide context information and help other developers to understand why a change was necessary or made in a specific way.
- ... to document how to use or configure a certain feature.
- ... to give context about a bug which has been resolved by the change.

These points are especially true for an open source project, where someone new can join at any time. Especially for these new contributors one should not underestimate the benefit of this additional information.

We all know that **writing** a good **commit message is effort**, but in the NeonBee community **we believe** that **it's worth it**.

## Conventional Commits

NeonBee is using [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) to ensure that the change log and releases notes can be generated automatically.

## Structure

There is a very good [blog post](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html) from Tim Pope, which explains how to write a commit message. It is not necessary to write commit messages which match exactly this proposal, but in order to really provide the benefits mentioned above the commit message should fulfill the following requirements:

- It **MUST** have a summary which is not longer than 80 characters and conforms to the [Conventional Commits](#conventional-commits) style.
- It **SHOULD** contain a more detailed explanatory text (at least few sentences) to describe the purpose of the change and its context.
- It **MAY** contain context additional information which helps to put this change into a bigger picture.

### Trivial Changes

Of course there are some kind of changes, which are really trivial like:

- bump of a version,
- fix for incorrect spelling
- refactoring with a small scope
- ...

For this kind of changes, no large more detailed explanatory text is required.

## Best Practices

Writing commit messages can be hard sometimes, especially if one is not used to it. Therefore this sections provide some best practices which may help when writing commit messages.

### Summary

In order to find a good summary for the commit message it might help to always start with the following words in your mind and then continuing with the summary:

> If you pick this commit, it will ...

### Explanatory Text

Always ask yourself, which information would another developer consider helpful when looking at the commit the first time. Maybe these questions help:

- What is the purpose of this change about?
- Why is this change required?
- Is there a reason why it is implemented in a specific way?
- Does this change require that a specific behavior don't change?
- ...

## Examples

Add examples as soon as there are good ones in the NeonBee repository.
