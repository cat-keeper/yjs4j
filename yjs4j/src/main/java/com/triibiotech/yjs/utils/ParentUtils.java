package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.types.AbstractType;

/**
 * Utility functions for parent-child relationships in Yjs documents.
 * Matches the functionality of isParentOf.js from the original Yjs implementation.
 *
 * @author zbs
 * @date 2025/10/28  08:53:04
 */
@SuppressWarnings("unused")
public class ParentUtils {

    /**
     * Check if parent is a parent of child.
     * This function traverses the parent chain to determine the relationship.
     *
     * @param parent The potential parent type
     * @param child The potential child item
     * @return true if parent is a parent of child, false otherwise
     */
    public static boolean isParentOf(AbstractType<?> parent, Item child) {
        if (parent == null || child == null) {
            return false;
        }

        // Traverse up the parent chain
        Object currentParent = child.parent;

        while (currentParent != null) {
            if (currentParent == parent) {
                return true;
            }

            // Move up the parent chain
            if (currentParent instanceof AbstractType<?> parentType) {
                Item parentItem = parentType.getItem();
                if (parentItem != null) {
                    currentParent = parentItem.parent;
                } else {
                    // Reached root level
                    break;
                }
            } else if (currentParent instanceof Item) {
                currentParent = ((Item) currentParent).parent;
            } else {
                // Unknown parent type
                break;
            }
        }

        return false;
    }

    /**
     * Check if parent is a parent of child type.
     *
     * @param parent The potential parent type
     * @param child The potential child type
     * @return true if parent is a parent of child, false otherwise
     */
    public static boolean isParentOf(AbstractType<?> parent, AbstractType<?> child) {
        if (parent == null || child == null) {
            return false;
        }

        if (parent == child) {
            return false;
        }

        Item childItem = child.getItem();
        if (childItem == null) {
            return false;
        }

        return isParentOf(parent, childItem);
    }

    /**
     * Find the common parent of two items.
     *
     * @param item1 First item
     * @param item2 Second item
     * @return The common parent, or null if no common parent exists
     */
    public static AbstractType<?> findCommonParent(Item item1, Item item2) {
        if (item1 == null || item2 == null) {
            return null;
        }

        // Get all parents of item1
        java.util.Set<AbstractType<?>> parents1 = new java.util.LinkedHashSet<>();
        Object current = item1.parent;

        while (current instanceof AbstractType<?> parentType) {
            parents1.add(parentType);

            Item parentItem = parentType.getItem();
            if (parentItem != null) {
                current = parentItem.parent;
            } else {
                break;
            }
        }

        // Check parents of item2 against parents1
        current = item2.parent;
        while (current instanceof AbstractType<?> parentType) {
            if (parents1.contains(parentType)) {
                return parentType;
            }

            Item parentItem = parentType.getItem();
            if (parentItem != null) {
                current = parentItem.parent;
            } else {
                break;
            }
        }

        return null;
    }

    /**
     * Get the root type of an item.
     *
     * @param item The item to find the root for
     * @return The root type, or null if not found
     */
    public static AbstractType<?> getRootType(Item item) {
        if (item == null) {
            return null;
        }

        Object current = item.parent;
        AbstractType<?> lastType = null;

        while (current instanceof AbstractType<?> parentType) {
            lastType = parentType;

            Item parentItem = parentType.getItem();
            if (parentItem != null) {
                current = parentItem.parent;
            } else {
                // Reached root level
                break;
            }
        }

        return lastType;
    }

    /**
     * Get the depth of an item in the document tree.
     *
     * @param item The item to calculate depth for
     * @return The depth (0 for root level items)
     */
    public static int getDepth(Item item) {
        if (item == null) {
            return -1;
        }

        int depth = 0;
        Object current = item.parent;

        while (current instanceof AbstractType<?> parentType) {
            depth++;

            Item parentItem = parentType.getItem();
            if (parentItem != null) {
                current = parentItem.parent;
            } else {
                break;
            }
        }

        return depth;
    }

    /**
     * Check if an item is at the root level (has no parent).
     *
     * @param item The item to check
     * @return true if the item is at root level
     */
    public static boolean isRootLevel(Item item) {
        if (item == null) {
            return false;
        }

        if (!(item.parent instanceof AbstractType<?> parentType)) {
            return true;
        }

        return parentType.getItem() == null;
    }

    /**
     * Get all ancestor types of an item.
     *
     * @param item The item to get ancestors for
     * @return A list of ancestor types, ordered from immediate parent to root
     */
    public static java.util.List<AbstractType<?>> getAncestors(Item item) {
        java.util.List<AbstractType<?>> ancestors = new java.util.ArrayList<>();

        if (item == null) {
            return ancestors;
        }

        Object current = item.parent;

        while (current instanceof AbstractType<?> parentType) {
            ancestors.add(parentType);

            Item parentItem = parentType.getItem();
            if (parentItem != null) {
                current = parentItem.parent;
            } else {
                break;
            }
        }

        return ancestors;
    }
}
