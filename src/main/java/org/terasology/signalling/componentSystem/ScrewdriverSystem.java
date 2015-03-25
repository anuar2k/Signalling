/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.signalling.componentSystem;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.math.Side;
import org.terasology.math.SideBitFlag;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.signalling.components.ScrewdriverComponent;
import org.terasology.signalling.components.SignalConsumerComponent;
import org.terasology.signalling.components.SignalGateComponent;
import org.terasology.signalling.components.SignalGateRotatedComponent;
import org.terasology.signalling.components.SignalProducerComponent;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.family.BlockFamily;
import org.terasology.world.block.family.SideDefinedBlockFamily;

import java.util.EnumMap;

@RegisterSystem(RegisterMode.AUTHORITY)
public class ScrewdriverSystem extends BaseComponentSystem {
    @In
    private WorldProvider worldProvider;
    @In
    private BlockEntityRegistry blockEntityRegistry;

    private EnumMap<Side, Side> sideOrder = new EnumMap<>(Side.class);

    @Override
    public void initialise() {
        sideOrder.put(Side.FRONT, Side.LEFT);
        sideOrder.put(Side.LEFT, Side.BACK);
        sideOrder.put(Side.BACK, Side.RIGHT);
        sideOrder.put(Side.RIGHT, Side.TOP);
        sideOrder.put(Side.TOP, Side.BOTTOM);
        sideOrder.put(Side.BOTTOM, Side.FRONT);
    }

    @ReceiveEvent(components = {ScrewdriverComponent.class})
    public void rotateGate(ActivateEvent event, EntityRef screwdriver) {
        final EntityRef target = event.getTarget();
        if (target.hasComponent(SignalGateComponent.class)) {
            final Vector3i targetLocation = new Vector3i(event.getTargetLocation());
            final Block block = worldProvider.getBlock(targetLocation);
            final BlockFamily blockFamily = block.getBlockFamily();
            if (blockFamily instanceof SideDefinedBlockFamily) {
                final SideDefinedBlockFamily sideDefinedBlockFamily = (SideDefinedBlockFamily) blockFamily;
                // Figure out the next block and side
                Side newSide = block.getDirection();
                Block blockForSide;
                do {
                    newSide = sideOrder.get(newSide);
                    blockForSide = sideDefinedBlockFamily.getBlockForSide(newSide);
                } while (blockForSide == null);

                if (worldProvider.setBlock(targetLocation, blockForSide) != null) {
                    final EntityRef gateEntity = blockEntityRegistry.getBlockEntityAt(targetLocation);

                    final SignalProducerComponent signalProducer = gateEntity.getComponent(SignalProducerComponent.class);
                    final SignalConsumerComponent signalConsumer = gateEntity.getComponent(SignalConsumerComponent.class);

                    signalConsumer.connectionSides = 0;
                    gateEntity.saveComponent(signalConsumer);

                    final byte newSideBit = SideBitFlag.getSide(newSide);
                    signalProducer.connectionSides = newSideBit;
                    signalConsumer.connectionSides = (byte) (63 - newSideBit);

                    gateEntity.saveComponent(signalProducer);
                    gateEntity.saveComponent(signalConsumer);

                    if (newSide == Side.FRONT) {
                        gateEntity.removeComponent(SignalGateRotatedComponent.class);
                    } else if (!gateEntity.hasComponent(SignalGateRotatedComponent.class)) {
                        gateEntity.addComponent(new SignalGateRotatedComponent());
                    }
                }
            }
        }
    }
}
