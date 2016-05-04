package org.onosproject.SdnCompetitionWay2;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.EthType;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.*;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by janon Wang on 5/3/16.
 */
@Component(immediate = false)
@Service
public class ValidateWay2 implements ValidateWay2Service {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private Map<DeviceId, Iterable<FlowEntry>> flowCopy;

    @Activate
    protected void activate() {
        log.info("Sdn Competition T4 Validate Way2 Started");
    }

    /**
     * .
     */
    @Deactivate
    protected void deactivate() {
        log.info("Sdn Competition T4 Validate Way2 Stopped");
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private EdgePortService edgePortService;

    @Override
    public ValidatePacket validateWay2(String srcIp, String dstIp) {

        flowCopy = new HashMap<>();
        flowCopy.clear();
        Iterator<Device> allDevice = deviceService.getDevices().iterator();
        while (allDevice.hasNext()) {

            Device thisDevice = allDevice.next();
            flowCopy.put(thisDevice.id(),
                    flowRuleService.getFlowEntries(thisDevice.id()));
        }

        Set<Host> srcHosts = hostService.getHostsByIp(IpAddress.valueOf(srcIp));
        MacAddress srcMac = srcHosts.iterator().next().mac();
        Set<Host> dstHost = hostService.getHostsByIp(IpAddress.valueOf(dstIp));
        MacAddress dstMac = dstHost.iterator().next().mac();

        return validateWayManager(srcMac, dstMac);
    }

    private ValidatePacket validateWayManager(MacAddress srcMac, MacAddress dstMAc) {
        ValidatePacket validatePacket = new ValidatePacket();

        Set<Criterion> Criterions = new HashSet<>();
        Criterions.add(Criteria.matchEthSrc(srcMac));
        Criterions.add(Criteria.matchEthDst(dstMAc));


        Host srcHost = hostService.getHostsByMac(srcMac).iterator().next();
        validatePacket.setsrcLocation(srcHost.location());
        validatePacket.setdstLocation(hostService.getHostsByMac(dstMAc).iterator().next().location());

        Device hostConnect = deviceService.getDevice(srcHost.location().deviceId());

        PortNumber inportNumber = srcHost.location().port();
        Criterions.add(Criteria.matchInPort(inportNumber));

        validatePacket.matchBuilder(Criterions);

        boolean arrived = validatePathRecursion(hostConnect, validatePacket);
        if(arrived) {
            log.info("the packet arrived successfully");
        }
        return validatePacket;
    }

    private boolean validatePathRecursion(Device device, ValidatePacket validatePacket) {
        ArrayList<FlowEntry> flowEntries = sortFlowTable(flowCopy.get(device.id()));
        FlowEntry matchFlow = flowEntries.get(0);
        boolean isMatch = false;
        //find the match flow
        for (int i = 0; i < flowEntries.size(); i++) {

            FlowEntry flow = flowEntries.get(i);

            boolean matchResult = matchFlowEntry(flow, validatePacket);
            if (matchResult) {
                matchFlow = flowEntries.get(i);
                isMatch = true;
                break;
            }
        }
        if (!isMatch) {
            log.info(device.id().toString()+"does not has match flows");
            return false;
        } else {
            List<Instruction> inst = matchFlow.treatment().immediate();
            for (Instruction instone : inst) {
                switch (instone.type()) {
                    case L2MODIFICATION:
                        break;
                    case L3MODIFICATION:
                        break;
                    case L4MODIFICATION:
                        break;
                    case OUTPUT:
                        Instructions.OutputInstruction instOutPut = (Instructions.OutputInstruction) instone;
                        if (!instOutPut.port().isLogical()) {
                            ConnectPoint instConnectionPoint = new ConnectPoint(device.id(), instOutPut.port());
                            //arrive at the edge point
                            if (edgePortService.isEdgePoint(instConnectionPoint)) {
                                Set<Host> dsthosts = hostService.getHostsByMac(((EthCriterion) validatePacket.
                                        getHeader(Criterion.Type.ETH_DST)).mac());
                                //if the packet arrived at the right destination
                                if (dsthosts.iterator().next().location().equals(instConnectionPoint)) {
                                    return true;
                                } else {
                                    log.info("the packet arrived at the wrong host");
                                    return false;
                                }
                                //arrive at the a device
                            } else{
                                //get the link from this device and filter them
                                Set<Link> allLink = linkService.getDeviceEgressLinks(device.id());
                                //filter-- to find the right dst link
                                Link dstLink = findLink(allLink, instOutPut.port());
                                validatePacket.pushPathLink(dstLink);
                                ConnectPoint dstPoint = dstLink.dst();
                                PortNumber nextInPortNumber = dstPoint.port();
                                //change the inport number
                                validatePacket.setHeader(Criteria.matchInPort(nextInPortNumber));
                                Device dstDevice = deviceService.getDevice(dstPoint.deviceId());
                                return validatePathRecursion(dstDevice, validatePacket);
                            }
                        } else {
                            log.info("the output port is not logical, the flow rules are error");
                            return false;
                        }
                    default:break;
                }

            }
            log.info("no output action");
            return false;
        }

    }

    private Link findLink(Set<Link> allLink, PortNumber portNumber) {
        Set<Link> dstLinks = new HashSet<>();
        Iterator<Link> it = allLink.iterator();
        while (it.hasNext()) {
            Link tmpDstLink = it.next();
            if (tmpDstLink.src().port().equals(portNumber)) {
                dstLinks.add(tmpDstLink);
                break;
            }
        }
        return dstLinks.iterator().next();
    }

    /**
     * Check if the flowEntry match the forwarding packet.
     *
     * @param flowEntry   the flowEntry get from the device
     * @param pkt   the forwarding packet
     * @return true when the flowEntry match the packet
     */
    private boolean matchFlowEntry(FlowEntry flowEntry, ValidatePacket pkt) {
        final int tcpTs = 6;
        final int udpTs = 17;
        ArrayList<Criterion> criterionArray = sortCriteria(flowEntry.selector().criteria());

        for (Criterion criterion : criterionArray) {
            switch (criterion.type()) {
                case IN_PORT:
                case ETH_SRC:
                case ETH_DST:
                case ETH_TYPE:
                    if (!matchExactly(pkt, criterion)) {
                        return false;
                    }
                    break;
                case VLAN_VID:
                case VLAN_PCP:
                    if (!pkt.existHeader(Criterion.Type.ETH_TYPE) ||
                            !((EthTypeCriterion) pkt.getHeader(Criterion.Type.ETH_TYPE))
                                    .ethType().equals(EthType.EtherType.VLAN)) {
                        return false;
                    }
                    if (!matchExactly(pkt, criterion)) {
                        return false;
                    }
                    break;
                case IPV4_SRC:
                case IPV4_DST:
                    if (!pkt.existHeader(Criterion.Type.ETH_TYPE) ||
                            !((EthTypeCriterion) pkt.getHeader(Criterion.Type.ETH_TYPE))
                                    .ethType().equals(EthType.EtherType.IPV4)) {
                        return false;
                    }
                    if (!matchAddIPV4(pkt, criterion)) {
                        return false;
                    }
                    break;
                case IP_PROTO:
                    if (!pkt.existHeader(Criterion.Type.ETH_TYPE) ||
                            !((EthTypeCriterion) pkt.getHeader(Criterion.Type.ETH_TYPE))
                                    .ethType().equals(EthType.EtherType.IPV4)) {
                        return false;
                    }
                    if (!matchExactly(pkt, criterion)) {
                        return false;
                    }
                    break;
                case IP_DSCP:
                    break;
                case IP_ECN:
                    break;
                case TCP_SRC:
                case TCP_DST:
                    if (!pkt.existHeader(Criterion.Type.IP_PROTO) ||
                            tcpTs != ((IPProtocolCriterion) pkt.getHeader(Criterion.Type.IP_PROTO)).protocol()) {
                        return false;
                    }
                    if (!matchExactly(pkt, criterion)) {
                        return false;
                    }
                    break;
                case UDP_SRC:
                case UDP_DST:
                    if (!pkt.existHeader(Criterion.Type.IP_PROTO) ||
                            udpTs != ((IPProtocolCriterion) pkt.getHeader(Criterion.Type.IP_PROTO)).protocol()) {
                        return false;
                    }
                    if (!matchExactly(pkt, criterion)) {
                        return false;
                    }
                    break;

                default:
                    break;
            }
        }
        return true;
    }
    /**
     * Check if the criterion matching.
     *
     * @param pkt   the forwarding packet
     * @param criterion   the criterion
     * @return true when the criterion matched
     */
    private boolean matchExactly(ValidatePacket pkt, Criterion criterion) {
        return pkt.existHeader(criterion.type()) &&
                pkt.getHeader(criterion.type()).equals(criterion);
    }

    /**
     * .
     * @param pkt       :.
     * @param criterion :.
     * @return .
     */
    private boolean matchAddIPV4(ValidatePacket pkt, Criterion criterion) {
        if (pkt.existHeader(criterion.type())) {
            IpPrefix ipFlow = ((IPCriterion) criterion).ip();
            IpPrefix ipPkt = ((IPCriterion) pkt.getHeader(criterion.type())).ip();
            return ipFlow.equals(ipPkt) || ipFlow.contains(ipPkt);
        } else {
            return false;
        }
    }

    private static ArrayList<FlowEntry> sortFlowTable(Iterable<FlowEntry> flowEntries) {

        ArrayList<FlowEntry> flows = new ArrayList<>((HashSet<FlowEntry>) flowEntries);

        Collections.sort(flows,
                (f1, f2) -> -(f1.priority() - f2.priority()));
        return flows;
    }

    /**
     * Sort the criterion according to the flowEntry type.
     *
     * @param criterionSet   the criterion to sort
     * @return the criterion in order
     */
    private static ArrayList<Criterion> sortCriteria(Set<Criterion> criterionSet) {

        ArrayList<Criterion> array = new ArrayList<>(criterionSet);
        Collections.sort(array,
                (c1, c2) -> c1.type().compareTo(c2.type()));
        return array;
    }
}
