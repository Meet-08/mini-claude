# mini-claude

Mini Claude is a terminal-based coding assistant built with Spring Boot and Spring AI. It runs as an interactive CLI,
connects to Azure OpenAI, and exposes file, search, glob, shell, and skill-based tools so the model can help inspect and
work with local codebases.

## What it does

- Starts a command-line chat loop for codebase assistance
- Uses Azure OpenAI for chat completion
- Provides tool access for:
    - reading and editing files
    - searching text with grep-style queries
    - discovering files with glob patterns
    - running shell commands
    - loading custom skills from `.agents/skills`
- Keeps a short chat memory during the session

## Requirements

- Java 25
- Maven Wrapper (`mvnw` / `mvnw.cmd`)
- An Azure OpenAI resource
- Environment variables for Azure OpenAI credentials

## Configuration

The application reads Azure OpenAI settings from environment variables:

| Variable                | Purpose                               |
|-------------------------|---------------------------------------|
| `AZURE_OPENAI_API_KEY`  | API key for the Azure OpenAI resource |
| `AZURE_OPENAI_ENDPOINT` | Azure OpenAI endpoint URL             |

Default chat settings are defined in `src/main/resources/application.yaml`:

- Deployment name: `o4-mini`
- Temperature: `1`
- Logging for Spring AI and agent classes is set to `DEBUG`

If you want to use a different deployment, update:

```yaml
spring:
  ai:
    azure:
      openai:
        chat:
          options:
            deployment-name: your-deployment-name
```

## Run locally

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

The app starts an interactive prompt:

```text
🤖 Mini Claude Code Agent at your service. Ask me anything about your codebase
>
```

Type a message and press Enter. To leave the session, type:

```text
exit
```

## Build and test

```bash
./mvnw test
./mvnw package
```

On Windows:

```bash
mvnw.cmd test
mvnw.cmd package
```

## How the agent is wired

The main application:

- creates a `ChatClient`
- sets a system prompt that tells the model it is a coding assistant
- registers tool callbacks from `.agents/skills`
- adds file, grep, glob, and shell tools
- keeps message-window chat memory for the current session

That means the assistant can inspect your workspace and act on the local project context instead of responding as a
generic chat bot.
