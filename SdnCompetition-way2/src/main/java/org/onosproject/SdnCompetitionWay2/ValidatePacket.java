package org.onosproject.SdnCompetitionWay2;

import org.onosproject.net.*;
import org.onosproject.net.flow.criteria.Criterion;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

/**
 * Created by janon on 5/3/16.
 */
public class ValidatePacket {
    private Map<Criterion.Type, Criterion> match;
    private Stack<Link> pathLink;
    private ConnectPoint srcLocation;
    private ConnectPoint dstLocation;

    ValidatePacket() {
        match = new HashMap<>();
        pathLink = new Stack<>();
        srcLocation = new ConnectPoint(DeviceId.NONE, PortNumber.P0);
        dstLocation = new ConnectPoint(DeviceId.NONE, PortNumber.P0);
    }

    public void matchBuilder(Iterable<Criterion> criterions) {
        for (Criterion criterion : criterions) {
            match.put(criterion.type(), criterion);
        }
    }

    public boolean pushPathLink(Link link) {
        pathLink.push(link);
        return true;
    }

    public boolean setsrcLocation(ConnectPoint tmpsrcLocation) {
        srcLocation = tmpsrcLocation;
        return true;
    }

    public boolean setdstLocation(ConnectPoint tmpdstLocation) {
        dstLocation = tmpdstLocation;
        return true;
    }

    public boolean setHeader(Criterion criterion) {
        match.put(criterion.type(), criterion);
        return true;
    }

    public Criterion getHeader(Criterion.Type criterionType) {
        return match.get(criterionType);
    }

    public boolean existHeader(Criterion.Type criterionType) {
        return match.containsKey(criterionType);
    }

    public void showPath() {
        if(pathLink.isEmpty()) {
            System.out.print("the source and the dst host are on the same device\n");
        } else {
            System.out.print("The path that the packet walk through is below:\n");
            System.out.print("Destination Host\n      |\n" +
                    dstLocation.toString() + "\n      |\n");
            while (!pathLink.isEmpty()) {
                Link showLink = pathLink.pop();
                ConnectPoint showSrc = showLink.src();
                ConnectPoint showDst = showLink.dst();
                System.out.print(showDst.toString() + "\n      |\n" +
                        showSrc.toString() + "\n      |\n");
            }
            System.out.print(srcLocation.toString() + "\n      |\nSource Host\n");
        }
    }

}
