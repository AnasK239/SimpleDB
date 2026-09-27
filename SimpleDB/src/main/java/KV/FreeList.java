package KV;

import BTree.*;
import BTree.LNode;

import java.util.Arrays;

public class FreeList {
    private final PageGet pageGetter;
    private final PageAllocate pageAppender;
    private final PageWrite pageWriter;

    private long headPage;
    private long headSeq;

    private long tailPage;
    private long tailSeq;

    private long maxSeq;

    public FreeList(PageGet pageGetter,
                    PageAllocate pageAppender,
                    PageWrite pageWriter
    ) {
        this.pageGetter = pageGetter;
        this.pageAppender = pageAppender;
        this.pageWriter = pageWriter;
    }

    record State(
            long headPage,
            long headSeq,
            long tailPage,
            long tailSeq
    ){}

    State getSnapshot(){
        return new State(
                headPage,
                headSeq,
                tailPage,
                tailSeq
        );
    }

    void restore(State state){
        headPage = state.headPage;
        headSeq = state.headSeq;
        tailPage = state.tailPage;
        tailSeq = state.tailSeq;
        maxSeq = tailSeq;
    }

    public void setMaxSeq() {
        maxSeq = tailSeq;
    }

    private static int seqToIndex(long seq) {
        return (int) (seq % LNode.CAPACITY);
    }

    private record PopResult(long page , long removedHead){}

    private PopResult popInternal() {
        if(headSeq >= maxSeq) {
            return new PopResult(0,0);
        }

        LNode node = new LNode(pageGetter.get(headPage));

        long pageNo = node.getPtr(seqToIndex(headSeq));

        if(pageNo <= 0){
            throw new IllegalStateException("Invalid page number in free list: " + pageNo);
        }

        headSeq++;
        long removedHead = 0;

        if(seqToIndex(headSeq) == 0){
            long next = node.getNext();

            if(next == 0){
                throw new IllegalStateException("Free list must retain a head page");
            }

            removedHead = headPage;
            headPage = next;
        }

        return new PopResult(pageNo, removedHead);
    }

    public long popHead(){
        PopResult popResult = popInternal();

        if(popResult.removedHead != 0){
            pushTail(popResult.removedHead());
        }

        return popResult.page();
    }

    public void pushTail(long pageNumber){

        if(pageNumber <= 0){
            throw new IllegalArgumentException("Cannot recycle page zero or negative page number: " + pageNumber);
        }

        LNode tail = new LNode(pageWriter.write(tailPage));

        tail.setPtr(seqToIndex(tailSeq) , pageNumber);
        tailSeq++;

        if(seqToIndex(tailSeq) != 0) return;

        PopResult popResult = popInternal();
        long nextPage = popResult.page();

        if(nextPage == 0){
            nextPage = pageAppender.allocate(new byte[BTREE.PAGE_SIZE]);
        }else{

            Arrays.fill(
                    pageWriter.write(nextPage),
                    (byte) 0
            );

        }

        tail.setNext(nextPage);
        tailPage = nextPage;

        if(popResult.removedHead != 0){
            LNode newTail = new LNode(pageWriter.write(tailPage));

            newTail.setPtr(0 , popResult.removedHead());
            tailSeq++;
        }

    }
}
