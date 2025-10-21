package com.embabel.grouper;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Verbosity;
import com.embabel.grouper.agent.GrouperProperties;
import com.embabel.grouper.domain.MessageVariantsRepository;
import com.embabel.grouper.domain.Model;
import com.embabel.grouper.domain.ParticipantRepository;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

@ShellComponent
record GrouperShell(
        AgentPlatform agentPlatform,
        ParticipantRepository participantRepository,
        MessageVariantsRepository messageVariantsRepository,
        GrouperProperties config) {

    @ShellMethod("Test a given message against a given group")
    String focusGroup(
            @ShellOption(help = "The id of the message", defaultValue = "smoking") String message,
            @ShellOption(help = "The group to test again", defaultValue = "english_teen") String group) {
        var participants = participantRepository.findByGroup(group);
        var messageVariants = messageVariantsRepository.findByName(message);

        if (participants.isEmpty()) {
            return "Unable to resolve participants by group " + group;
        }
        if (messageVariants == null) {
            return "Unable to find messaging for message " + message;
        }

        var focusGroup = new Model.FocusGroup(participants);
        var positioning = new Model.Positioning(List.of(messageVariants));

        var bestScoringVariants = AgentInvocation.builder(agentPlatform)
                .options(o -> o.verbosity(new Verbosity(config().showPrompts(), false, false, false)))
                .build(Model.BestScoringVariants.class)
                .invoke(focusGroup, participants, positioning);
        return bestScoringVariants.toString();
    }

}
