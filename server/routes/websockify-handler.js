var childProcess = require('child_process');
var ps = require('ps-node');

function WebsockifyHandler() {

    this.createProxy = function(ip) {

        // Create new proxy
        var cmd = '../external/noVNC/utils/launch.sh --vnc ' + ip + ':5901 --listen 6080'
        console.log('Running: ' + cmd);
        var child = childProcess.exec(cmd);

        child.stdout.on('data', function(data) {
            console.log('Websockify ('+ child.pid +') output: ' + data);
        });

        child.stderr.on('data', function(data) {
            console.log('Websockify ('+ child.pid +') error: ' + data);
        });

        child.on('close', function(code) {
            console.log('Websockify (' + child.pid + ') exited with code ' + code);
        });
        console.log('Websockify started with pid: ' + child.pid);
    }

    this.killProxies = function(callback) {
        ps.lookup({command: 'bash', arguments: '--vnc'}, function(err, resultList) {
            if (err) throw new Error(err);
            resultList.forEach(function(p) {
                if(p) console.log('bash PID: %s, COMMAND: %s, ARGUMENTS: %s', p.pid, p.command, p.arguments);
                try {
                    process.kill(p.pid);
                }
                catch(err) {
                    console.log('Error while killing: ' + p.pid +': ' + err.message);
                }
            });
            typeof callback === 'function' && callback();
        });
    }

    return this;
}

module.exports = WebsockifyHandler;
