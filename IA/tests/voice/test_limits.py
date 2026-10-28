import pytest

from app.voice.errors import VoiceErrorCode, VoiceRequestError
from app.voice.limits import UserRequestLimiter
from tests.voice.fakes import FakeClock


def rejected_code(limiter, user_id) -> VoiceErrorCode:
    with pytest.raises(VoiceRequestError) as error:
        with limiter.admit(user_id):
            pass
    return error.value.code


def test_allows_one_question_at_a_time_per_user():
    limiter = UserRequestLimiter(max_concurrent=1, max_per_minute=10)

    with limiter.admit(7):
        assert rejected_code(limiter, 7) is VoiceErrorCode.ASSISTANT_BUSY
        with limiter.admit(8):
            pass
    with limiter.admit(7):
        pass


def test_frees_the_slot_even_when_the_question_fails():
    limiter = UserRequestLimiter(max_concurrent=1, max_per_minute=10)

    with pytest.raises(RuntimeError):
        with limiter.admit(7):
            raise RuntimeError("falló")

    with limiter.admit(7):
        pass


def test_limits_questions_per_minute_and_forgets_old_ones():
    clock = FakeClock()
    limiter = UserRequestLimiter(max_concurrent=1, max_per_minute=2, clock=clock)
    for _ in range(2):
        with limiter.admit(7):
            pass

    assert rejected_code(limiter, 7) is VoiceErrorCode.RATE_LIMITED
    clock.advance(60)
    with limiter.admit(7):
        pass


def test_a_rejected_question_does_not_use_up_the_minute():
    limiter = UserRequestLimiter(max_concurrent=1, max_per_minute=2)

    with limiter.admit(7):
        rejected_code(limiter, 7)
    with limiter.admit(7):
        pass
