package com.embabel.grouper;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Verbosity;
import com.embabel.grouper.domain.InMemoryParticipantRepository;
import com.embabel.grouper.domain.Model;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.List;

@ShellComponent
record GrouperShell(AgentPlatform agentPlatform) {

    @ShellMethod("Run a focus group")
    String focus() {
        var participants = new InMemoryParticipantRepository().findAll();
        var focusGroup = new Model.FocusGroup(participants);

        var positioning = new Model.Positioning(List.of(
                new Model.MessageVariants(
                        new Model.Message("nosmoke",
                                "smoking is bad",
                                "To deter the participant from wanting to smoke",
                                """
                                        A concise, memorable slogan for use in a national campaign aimed at British teenagers
                                        Imagine it on the side of every second bus in London
                                        """),
                        "Don't smoke as it will kill you",
                        "Smoking is uncool",
                        "Smoking will give you cancer",
                        "Boys won't want to kiss you if you stink of cigarette smoke",
                        "Smoking makes you slow",
//                        "Smoking is for losers",
//                        "Winners don't smoke",
//                        "Smoking is boring",
//                        "Taylor Swift hates smoking",
                        "Look at old people who smoke. They look really bad. If they were your age, they wouldn't start"
                )
        ));

        // TODO get AgentProcess back also: Could be like Spring things, task Arjen
        var best = AgentInvocation.builder(agentPlatform)
                .options(o -> o.verbosity(new Verbosity(true, false, false, false)))
                .build(Model.BestScoringVariants.class)
                .invoke(focusGroup, participants, positioning);
        return best.toString();
    }

}
