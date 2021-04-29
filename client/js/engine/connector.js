import DefaultSender from "./defaultSender";

class Connector {
    constructor(params) {
        const url = params["url"];
        if (params["sender"] === "AJAX") {
            this.sender = new AJAXSender(url, params["callback"]);
        }
        else {
            this.sender = new WebSocketSender(url, params["callback"]);
        }
    }

    send(message) {
        this.sender.send(message);
    }
}

class AJAXSender{
    constructor(URL, callback) {
        this.url = URL;
        this.callback = callback;
    }
    send(message) {
        $.ajax({
            type: "POST",
            crossDomain: true,
            url: "https://" + this.url,
            data: message,
            dataType: "text",
            success: this.callback
        });
    }
}

class WebSocketSender{
    constructor(URL, callback) {
        this.url = URL;
        this.ws = new WebSocket("wss://" + this.url);
        this.ws.onmessage = callback;
    }

    setCallback(callback) {
        this.ws.onmessage = callback;
    }

    send(message) {
        if (this.ws.readyState !== this.ws.OPEN) {
            this.ws.onopen = () => {
                this.ws.send(message);
            };
        }
        else {
            if (this.ws.readyState === this.ws.CLOSED) {
                this.ws = new WebSocket("wss://" + this.url);
            }
            this.ws.send(message);
        }
    }
}

export default Connector;