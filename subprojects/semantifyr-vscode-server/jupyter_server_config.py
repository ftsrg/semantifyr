c = get_config()  # noqa: F821
c.ServerProxy.servers = {
    "semantifyr": {
        "command": [
            "openvscode-server",
            "--host", "0.0.0.0",
            "--without-connection-token",
            "--telemetry-level", "off",
            "--port", "{port}",
            "--default-folder", "/home/jovyan/examples",
        ],
        "absolute_url": False,
        "timeout": 300,
        "new_browser_tab": True,
        "launcher_entry": {
            "title": "Semantifyr",
        }
    }
}
# Listen on all interfaces (ipv4 and ipv6)
c.ServerApp.ip = ""
c.ServerApp.open_browser = False

# https://github.com/jupyter/notebook/issues/3130
c.FileContentsManager.delete_to_trash = False
