---
name: external
description: Use this skill for external API calls such as weather, logistics, exchange rate, and third-party service lookup.
---

# External API Skill

Use this skill when the user asks for third-party service data.

Call `callExternalApiTool` with the service type and required parameters. If a service-specific identifier is missing, ask for it before calling the tool. When a provider fails, explain the failure without inventing data.
