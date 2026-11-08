package plad.services.room;

import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import plad.exception.MeetingParticipantNotFoundException;
import plad.exception.RoomUnavailableException;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The real gateway against a mocked LiveKit client: what it asks for, and how every kind of failure (a refusal, no
// connection, no answer in time, a participant that is not in the room) turns into the exceptions the API maps.
// No network: the RoomServiceClient is a mock and the calls are mocks.
class LiveKitMeetingRoomGatewayTest {

    private static final Duration SHORT_DEADLINE = Duration.ofMillis(150);

    private final RoomServiceClient client = mock(RoomServiceClient.class);
    private final LiveKitMeetingRoomGateway gateway = new LiveKitMeetingRoomGateway(client, SHORT_DEADLINE);

    @Test
    void allowingSpeechSendsTheWholePermissionAndTakingItAwayCutsPublishing() {
        Call<LivekitModels.ParticipantInfo> call = answering(Response.success(null));
        when(client.updateParticipant(eq("room"), eq("id"), isNull(), isNull(), any())).thenReturn(call);

        gateway.setSpeakPermission("room", "id", true);
        gateway.setSpeakPermission("room", "id", false);

        ArgumentCaptor<LivekitModels.ParticipantPermission> sent = ArgumentCaptor.forClass(LivekitModels.ParticipantPermission.class);
        verify(client, org.mockito.Mockito.times(2)).updateParticipant(eq("room"), eq("id"), isNull(), isNull(), sent.capture());
        assertThat(sent.getAllValues().get(0).getCanPublish()).isTrue();
        assertThat(sent.getAllValues().get(1).getCanPublish()).isFalse();
    }

    @Test
    void removingAsksTheProviderToDropTheParticipantAndTheirTokens() {
        Call<Void> call = answering(Response.success(null));
        when(client.removeParticipant("room", "id")).thenReturn(call);

        assertThatCode(() -> gateway.removeParticipant("room", "id")).doesNotThrowAnyException();

        verify(client).removeParticipant("room", "id");
    }

    @Test
    void closingTheRoomAsksTheProviderToDeleteIt() {
        Call<Void> call = answering(Response.success(null));
        when(client.deleteRoom("room")).thenReturn(call);

        assertThatCode(() -> gateway.closeRoom("room")).doesNotThrowAnyException();

        verify(client).deleteRoom("room");
    }

    @Test
    void aParticipantThatIsNotInTheRoomIsNotFound() {
        Call<Void> removeCall = answering(failure(404));
        Call<LivekitModels.ParticipantInfo> updateCall = answering(failure(404));
        when(client.removeParticipant("room", "id")).thenReturn(removeCall);
        when(client.updateParticipant(eq("room"), eq("id"), isNull(), isNull(), any())).thenReturn(updateCall);

        assertThatThrownBy(() -> gateway.removeParticipant("room", "id")).isInstanceOf(MeetingParticipantNotFoundException.class);
        assertThatThrownBy(() -> gateway.setSpeakPermission("room", "id", true)).isInstanceOf(MeetingParticipantNotFoundException.class);
    }

    @Test
    void aRoomThatDoesNotExistIsAlreadyClosed() {
        Call<Void> call = answering(failure(404));
        when(client.deleteRoom("room")).thenReturn(call);

        assertThatCode(() -> gateway.closeRoom("room")).doesNotThrowAnyException();
    }

    @Test
    void aRefusalFromTheProviderIsUnavailable() {
        Call<Void> deleteCall = answering(failure(500));
        Call<Void> removeCall = answering(failure(401));
        when(client.deleteRoom("room")).thenReturn(deleteCall);
        when(client.removeParticipant("room", "id")).thenReturn(removeCall);

        assertThatThrownBy(() -> gateway.closeRoom("room")).isInstanceOf(RoomUnavailableException.class);
        assertThatThrownBy(() -> gateway.removeParticipant("room", "id")).isInstanceOf(RoomUnavailableException.class);
    }

    @Test
    void noConnectionIsUnavailableAndTheMessageNeverCarriesTheCause() {
        Call<Void> call = mockCall();
        try {
            when(call.execute()).thenThrow(new IOException("connect to https://secret-host.example:443 failed"));
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        when(client.deleteRoom("room")).thenReturn(call);

        assertThatThrownBy(() -> gateway.closeRoom("room"))
                .isInstanceOf(RoomUnavailableException.class)
                .hasMessageNotContaining("secret-host");
    }

    @Test
    void aProviderThatDoesNotAnswerInTimeIsUnavailableAndTheCallIsCancelled() throws Exception {
        Call<Void> call = mockCall();
        when(call.execute()).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return Response.success(null);
        });
        when(client.deleteRoom("room")).thenReturn(call);

        long started = System.nanoTime();
        assertThatThrownBy(() -> gateway.closeRoom("room")).isInstanceOf(RoomUnavailableException.class);
        long tookMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(tookMillis).isLessThan(3_000); // the deadline, not the provider's pace
        verify(call).cancel();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <T> Call<T> mockCall() {
        return mock(Call.class);
    }

    private static <T> Call<T> answering(Response<T> response) {
        Call<T> call = mockCall();
        try {
            when(call.execute()).thenReturn(response);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return call;
    }

    // a failed response without a body: the gateway then goes by the HTTP status
    @SuppressWarnings("unchecked")
    private static <T> Response<T> failure(int status) {
        Response<T> response = mock(Response.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.code()).thenReturn(status);
        return response;
    }
}
