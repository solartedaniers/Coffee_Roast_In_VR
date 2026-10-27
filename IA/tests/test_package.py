import app


def test_package_is_importable():
    assert app.__name__ == "app"
