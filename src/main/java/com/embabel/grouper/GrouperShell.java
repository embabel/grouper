package com.embabel.grouper;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.config.models.OpenAiModels;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Verbosity;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.grouper.agent.Domain;
import com.embabel.grouper.agent.PromptedParticipant;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.LinkedList;
import java.util.List;

@ShellComponent
record GrouperShell(AgentPlatform agentPlatform) {

    @ShellMethod("Demo")
    String demo() {
        var participants = new LinkedList<Domain.Participant>();
        var promptedParticipant = new PromptedParticipant(
                "Alice",
                LlmOptions.withModel(OpenAiModels.GPT_41_MINI),
                """
                        You are a 15 year old girl who lives in Richmond and loves Taylor Swift and tennis
                        """
        );
        participants.add(promptedParticipant);
        var focusGroup = new Domain.FocusGroup(participants);

        var positioning = new Domain.Positioning(List.of(
                new Domain.MessageTest(
                        new Domain.Message("nosmoke", "smoking is bad", "To deter the participant from wanting to smoke"),
                        "Don't smoke as it will kill you",
                        "Smoking is uncool",
                        "Smoking will give you cancer",
                        "Boys won't want to kiss you if you stink of cigarette smoke")
        ));

        var focusGroupRun = AgentInvocation.builder(agentPlatform)
                .options(o -> o.verbosity(new Verbosity(true, false, false, false)))
                .build(Domain.FocusGroupRun.class)
                .invoke(focusGroup, participants, positioning);
        return focusGroupRun.toString();
    }

}
