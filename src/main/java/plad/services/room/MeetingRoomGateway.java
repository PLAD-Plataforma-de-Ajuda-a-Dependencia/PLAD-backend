package plad.services.room;

import plad.exception.MeetingParticipantNotFoundException;
import plad.exception.RoomUnavailableException;

// The one way out to the video provider (LiveKit's room service). Every network call to it goes through here, so
// the tests replace it with a fake and never reach LiveKit. Signing a join token is local arithmetic, not a call:
// it lives in MeetingTokenFactory.
public interface MeetingRoomGateway {

    /**
     * Lets one participant publish audio and video (allowed) or takes it away again (not allowed); a participant that
     * loses it is cut off at once. Screen sharing is never granted this way.
     *
     * @throws MeetingParticipantNotFoundException the identity is not in the room (not connected)
     * @throws RoomUnavailableException            the provider did not answer or refused the call
     */
    void setSpeakPermission(String room, String identity, boolean allowed);

    /**
     * Drops one participant from the room and invalidates the tokens already issued to them.
     *
     * @throws MeetingParticipantNotFoundException the identity is not in the room (not connected)
     * @throws RoomUnavailableException            the provider did not answer or refused the call
     */
    void removeParticipant(String room, String identity);

    /**
     * Closes the room and disconnects everyone in it. A room that does not exist (nobody ever joined) is not an error.
     *
     * @throws RoomUnavailableException the provider did not answer or refused the call
     */
    void closeRoom(String room);
}
