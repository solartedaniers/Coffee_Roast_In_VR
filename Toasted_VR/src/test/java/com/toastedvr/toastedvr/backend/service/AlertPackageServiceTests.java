package com.toastedvr.toastedvr.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toastedvr.toastedvr.backend.client.AiAlertPackageClient;
import com.toastedvr.toastedvr.backend.client.AiAlertPackageReply;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AlertPackageServiceTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AiAlertPackageClient client = mock(AiAlertPackageClient.class);
    private final AlertPackageService service =
        new AlertPackageService(userRepository, client, mock(MessageResolver.class));

    @Test
    void shouldAskForThePackageOfTheLevelStoredForThePlayer() {
        User player = mock(User.class);
        when(player.getKnowledgeLevel()).thenReturn(KnowledgeLevel.ADVANCED);
        when(userRepository.findById(7L)).thenReturn(Optional.of(player));
        AiAlertPackageReply reply = new AiAlertPackageReply(304, "\"abc\"", new byte[0]);
        when(client.fetch(KnowledgeLevel.ADVANCED, "\"abc\"")).thenReturn(reply);

        assertThat(service.fetch(7L, "\"abc\"")).isSameAs(reply);
    }

    @Test
    void shouldLetTheAiServicePickTheDefaultWhenThePlayerHasNoLevel() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(mock(User.class)));

        service.fetch(7L, null);

        verify(client).fetch(null, null);
    }

    @Test
    void shouldFailWithoutCallingTheAiServiceWhenTheUserDoesNotExist() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetch(7L, null)).isInstanceOf(ResourceNotFoundException.class);
        verify(client, never()).fetch(any(), any());
    }
}
