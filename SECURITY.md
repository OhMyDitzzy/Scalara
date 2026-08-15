# Security Policy

## About Scalara's permission

Scalara requests `WRITE_SECURE_SETTINGS`, a `signature|privileged` Android
permission that must be granted manually (via ADB or a shell-access tool) —
see the [How it works](README.md#how-it-works) section of the README. Because
this permission can modify system-level settings, please pay particular
attention to:

- Whether the app could write settings beyond display size/density without
  explicit user action
- Whether the granted permission could be leveraged by another component
  (e.g. via an exported activity or a malicious intent) to perform
  unintended system changes

## Reporting a vulnerability

If you discover a security vulnerability in Scalara, please **do not** open a
public issue. Instead, report it privately via
[GitHub Security Advisories](../../security/advisories/new) for this
repository.

Please include as much detail as possible:

- A description of the vulnerability and its potential impact
- Steps to reproduce, or a proof of concept
- The version/commit affected

You should receive an initial response within a few days. Once a fix is
available, we'll coordinate disclosure and credit you (if you'd like) in the
release notes.

## Supported versions

As this project has not yet reached a stable 1.0 release, only the latest
tagged release is supported with security fixes.
