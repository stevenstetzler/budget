import os
import tempfile

import pytest

import app as flask_app


@pytest.fixture(scope="session")
def client():
    db_fd, db_path = tempfile.mkstemp(suffix=".db")
    flask_app.DATABASE = db_path
    flask_app.init_db()

    flask_app.app.config["TESTING"] = True
    with flask_app.app.test_client() as test_client:
        yield test_client

    os.close(db_fd)
    os.unlink(db_path)
