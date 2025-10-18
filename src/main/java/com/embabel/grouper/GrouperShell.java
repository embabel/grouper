package com.embabel.grouper;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.config.models.AnthropicModels;
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

    @ShellMethod("Run a focus group")
    String focus() {
        var participants = new LinkedList<Domain.Participant>();
        var llms = List.of(
//                LlmOptions.withModel(OpenAiModels.GPT_5_NANO),
                LlmOptions.withModel(OpenAiModels.GPT_41_NANO),
                LlmOptions.withModel(OpenAiModels.GPT_41_NANO).withTemperature(.7),
                LlmOptions.withModel(AnthropicModels.CLAUDE_HAIKU_4_5)
        );
        var alices = PromptedParticipant.against(
                "Alice",
                """
                        You are a 15 year old girl who lives in Richmond and loves Taylor Swift and tennis
                        """,
                llms
        );
        var ziffs = PromptedParticipant.against(
                "Ziff",
                """
                        You are a 17 year old non-binary AFAB who lives in Chertsey, hates sport and is passionate
                        about Gaza
                        """,
                llms
        );
        var toms = PromptedParticipant.against(
                "Tom",
                """
                        You are a 16 year old boy who lives in Chertsey and wants to study
                        PPE at Cambridge. You are a member of the young conservatives.
                        You go to the gym every day and have run a mile in 4:30.
                        """,
                llms
        );
        var jerries = PromptedParticipant.against(
                "Jerry",
                """
                        15 year old boy who has severe asthma and allergies.
                        Lives with his single mother in Woking.
                        Hopes to become a doctor.
                        """,
                llms
        );
        participants.addAll(alices);
        participants.addAll(ziffs);
        participants.addAll(toms);
        participants.addAll(jerries);
        var focusGroup = new Domain.FocusGroup(participants);

        var positioning = new Domain.Positioning(List.of(
                new Domain.MessageTest(
                        new Domain.Message("nosmoke", "smoking is bad",
                                "To deter the participant from wanting to smoke"),
                        "Don't smoke as it will kill you",
                        "Smoking is uncool",
                        "Smoking will give you cancer",
                        "Boys won't want to kiss you if you stink of cigarette smoke",
                        "Smoking makes you slow",
                        "Smoking is for losers",
                        "Winners don't smoke",
                        "Smoking is boring",
                        "Taylor Swift hates smoking",
                        "Look at old people who smoke. They look really bad. If they were your age, they wouldn't start"
                )
        ));

        var focusGroupRun = AgentInvocation.builder(agentPlatform)
                .options(o -> o.verbosity(new Verbosity(true, false, false, false)))
                .build(Domain.FocusGroupRun.class)
                .invoke(focusGroup, participants, positioning);
        return focusGroupRun.infoString(true, 5);
    }

}
