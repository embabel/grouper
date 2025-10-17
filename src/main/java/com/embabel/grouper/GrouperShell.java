package com.embabel.grouper;

import com.embabel.agent.core.AgentPlatform;
import org.springframework.shell.standard.ShellComponent;

@ShellComponent
record GrouperShell(AgentPlatform agentPlatform) {

//    @ShellMethod("Demo")
//    String demo() {
//        // Illustrate calling an agent programmatically,
//        // as most often occurs in real applications.
//        var reviewedStory = AgentInvocation
//                .create(agentPlatform, WriteAndReviewAgent.ReviewedStory.class)
//                .invoke(new UserInput("Tell me a story about caterpillars"));
//        return reviewedStory.getContent();
//    }

}
