package com.meet.miniclaude;

import org.springaicommunity.agent.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.Scanner;

@SpringBootApplication
public class MiniClaudeApplication {

    static void main(String[] args) {
        SpringApplication.run(MiniClaudeApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(ChatClient.Builder builder) {
        return args -> {
            ChatClient client = builder
                    .defaultSystem("""
                            You are helpful coding assistant. You have access to tools
                            for reading files, searching code, running shell commands,
                            and editing files. Use them to help the user with their codebases.
                            
                            Current directory: %s
                            """.formatted(System.getProperty("user.dir")))
                    .defaultToolCallbacks(SkillsTool.builder()
                            .addSkillsDirectory(".agents/skills")
                            .build()
                    )
                    .defaultTools(
                            FileSystemTools.builder().build(),
                            GrepTool.builder().build(),
                            GlobTool.builder().build(),
                            ShellTools.builder().build()
                    ).defaultAdvisors(
                            ToolCallAdvisor.builder().conversationHistoryEnabled(false).build(),
                            MessageChatMemoryAdvisor.builder(
                                    MessageWindowChatMemory.builder().maxMessages(50).build()
                            ).build()
                    )
                    .build();

            var scanner = new Scanner(System.in);

            System.out.println("🤖 Mini Claude Code Agent at your service. Ask me anything about your codebase");

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input.trim())) break;

                try {
                    String res = client.prompt(input)
                            .toolContext(Map.of("workingDirectory", System.getProperty("user.dir")))
                            .advisors(a -> a.param("chat_memory_conversation_id", "default"))
                            .call()
                            .content();

                    System.out.println("\n" + res);
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        };
    }
}
