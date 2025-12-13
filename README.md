> [!WARNING]
> Still On Development

# Overview
Scalara is a no-root resolution changer app that works through the `WRITE_SECURE_SETTINGS` permission protection. This app is a follow-up update to Project [Pixelify.](https://github.com/DitzDev/Pixelify)

# Understanding Scalara connection with Shizuku
Scalara works by interacting with Android system services to modify display-related configurations. These system services are part of the Android framework and are accessed through standard system APIs, such as those provided by the WindowManager.

When an application calls a system API, the request is not handled inside the app itself. Instead, it is sent through Android’s Binder IPC mechanism to the system server process. During this process, Android checks the identity (UID) of the caller and verifies whether the caller holds the required permissions to perform the operation.

Operations related to display configuration are protected by the WRITE_SECURE_SETTINGS permission. This permission is restricted to system-level contexts and cannot be granted directly to normal user-installed applications. As a result, an app like Scalara cannot access these APIs on its own.

Shizuku provides a solution by running a dedicated server process with elevated privileges, started by the user via ADB or root. When Scalara is launched, it receives a Binder connection to the Shizuku server. Through this connection, Scalara can make requests that are forwarded to the system server from a privileged context.

Because the system server evaluates permissions based on the calling process of the Binder transaction, requests forwarded by Shizuku pass the permission checks required for secure system APIs. This allows Scalara to use the same system APIs as system applications, while remaining a no-root app.

In summary, Shizuku acts as a Binder-based privilege bridge between Scalara and the Android system server, enabling access to protected system APIs that require WRITE_SECURE_SETTINGS.