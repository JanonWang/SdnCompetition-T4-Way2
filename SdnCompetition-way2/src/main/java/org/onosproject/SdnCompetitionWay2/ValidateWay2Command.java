package org.onosproject.SdnCompetitionWay2;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;

/**
 * Created by janon on 5/3/16.
 */
@Command(scope = "onos", name = "validate-2",
        description = "find the black holes and fix them")
public class ValidateWay2Command extends AbstractShellCommand {

    @Argument(index = 0, name = "IpPair", description = "source Ip address and destination Ip address",
            required = true, multiValued = false)
    private String IpPair = null;

    @Override
    protected void execute() {
        final int headLength = 2;
        ValidateWay2Service service = get(ValidateWay2Service.class);

        String[] Ips = IpPair.split(",");
        if (headLength != Ips.length) {
            System.out.print("input error!");
            return;
        }
        String srcIp = Ips[0];
        String dstIp = Ips[1];

        ValidatePacket result = service.validateWay2(srcIp, dstIp);
        result.showPath();
    }


}
